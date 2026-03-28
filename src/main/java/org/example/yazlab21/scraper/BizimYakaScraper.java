package org.example.yazlab21.scraper;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BizimYakaScraper {

    private final HaberRepository haberRepository;

    public void scrapeBizimYaka() {
        LocalDateTime ucGunOnce = LocalDateTime.now().minusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
        DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        System.out.println("📅 3 Gün Sınırı: " + ucGunOnce.format(logFormat));

        List<String> kategoriLinkleri = Arrays.asList(
                "https://www.bizimyaka.com/arsiv/kocaeli-son-dakika-haberleri",
                "https://www.bizimyaka.com/arsiv/kocaeli-asayis-haberleri"
        );

        for (String baseUrl : kategoriLinkleri) {
            boolean eskiHaberSiniri = false;
            int sayfaNumarasi = 1;
            
            while (!eskiHaberSiniri) {
                try {
                    String url = sayfaNumarasi == 1 ? baseUrl : baseUrl + "/" + sayfaNumarasi;
                    System.out.println("\n🌍 Bizim Yaka Taranıyor (Sayfa " + sayfaNumarasi + "): " + url);

                    Document document = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .timeout(30000)
                            .get();

                    Elements haberKartlari = document.select(".post");
                    
                    if (haberKartlari.isEmpty()) {
                        System.out.println("⏩ Son sayfaya ulaşıldı, diğer kategoriye geçiliyor...");
                        break;
                    }
                    
                    System.out.println("📊 Sayfada " + haberKartlari.size() + " haber kartı bulundu.");

                    for (Element kart : haberKartlari) {
                        Element aTag = kart.select("h3.b a").first();
                        if (aTag == null) continue;

                        String baslik = aTag.text();
                        String link = aTag.absUrl("href").replace(" ", "%20");
                        String ozet = kart.select("p.cut-2").text();

                        if (haberRepository.existsByLink(link)) {
                            continue;
                        }

                        try {
                            Thread.sleep(1500);

                            Document detay = Jsoup.connect(link)
                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                    .timeout(30000)
                                    .get();

                            String tarihStr = detay.select("meta[name='datePublished']").attr("content");
                            if (tarihStr.isEmpty()) {
                                tarihStr = detay.select("meta[property='article:published_time']").attr("content");
                            }
                            if (tarihStr.isEmpty()) {
                                tarihStr = detay.select("time").attr("datetime");
                            }

                            if (!tarihStr.isEmpty()) {
                                LocalDateTime haberZamani;
                                try {
                                    haberZamani = OffsetDateTime.parse(tarihStr).atZoneSameInstant(ZoneId.of("Europe/Istanbul")).toLocalDateTime();
                                } catch (Exception e) {
                                    haberZamani = LocalDateTime.now();
                                }

                                String tamIcerik = detay.select(".article-body, article, .news-content, .post-content, .content").text();
                                if (tamIcerik.isEmpty()) {
                                    tamIcerik = ozet;
                                }

                                if (haberZamani.isBefore(ucGunOnce)) {
                                    System.out.println("⏳ [ESKİ HABER ATLANDI] " + baslik);
                                    eskiHaberSiniri = true;
                                    break;
                                }

                                String tur = haberTuruBelirle(baslik, tamIcerik);

                                if (tur == null) {
                                    continue;
                                }

                                Haber yeniHaber = Haber.builder()
                                        .baslik(baslik)
                                        .icerik(tamIcerik)
                                        .haberTuru(tur)
                                        .link(link)
                                        .kaynakAd("Bizim Yaka")
                                        .yayinTarihi(haberZamani)
                                        .build();

                                haberRepository.save(yeniHaber);
                                System.out.println("✅ KAYDEDİLDİ [" + tur + "] - " + haberZamani.format(logFormat) + " : " + baslik);
                            }

                        } catch (Exception e) {
                            System.err.println("❌ Detay çekilemedi: " + link + " - " + e.getMessage());
                        }
                    }

                    if (eskiHaberSiniri) {
                        System.out.println("⏩ 3 günlük sınır bitti, diğer kategoriye geçiliyor...");
                        break;
                    }

                    sayfaNumarasi++;
                    Thread.sleep(3000);

                } catch (Exception e) {
                    System.err.println("❌ Sayfa Hata (Sayfa " + sayfaNumarasi + "): " + e.getMessage());
                    break;
                }
            }
        }
        System.out.println("\n🏁 Scraping işlemi tamamlandı.");
    }

    private String haberTuruBelirle(String baslik, String icerik) {
        // 1. ADIM: Noktalama işaretlerini silip, saf kelime dizisi elde ediyoruz
        String safMetin = (baslik + " " + icerik).toLowerCase(new Locale("tr"));
        // Tüm noktalama işaretlerini boşluğa çevir ve başa/sona boşluk ekle ki kelimeler netleşsin
        String kelimeler = " " + safMetin.replaceAll("[^a-zğüşıöç]", " ") + " ";


        if (safMetin.contains("mahkeme") || safMetin.contains("duruşma") || safMetin.contains("sanık") ||
                safMetin.contains("yargılan") || safMetin.contains("hakim ") || safMetin.contains("dava") ||
                safMetin.contains("cezaev") || safMetin.contains("müebbet") || safMetin.contains("beraat") ||
                kelimeler.contains(" kutladı ") || kelimeler.contains(" bayram ") || kelimeler.contains(" mesaj ") ||
                kelimeler.contains(" maç ") || kelimeler.contains(" şampiyon ") || kelimeler.contains(" turnuva "))
                {

            return null;
        }


        boolean kazaEylemi = kelimeler.contains(" kaza ") || safMetin.contains("çarpıştı") || safMetin.contains("takla attı") || safMetin.contains("şarampole");
        boolean motorluArac = kelimeler.contains(" araç ") || kelimeler.contains(" otomobil ") || kelimeler.contains(" motosiklet ") || kelimeler.contains(" otobüs ") || kelimeler.contains(" kamyon ") || kelimeler.contains(" tır ");

        if (kazaEylemi && motorluArac && !safMetin.contains("iş kazası")) {
            return "Trafik Kazası";
        }
        boolean yanginEylemi = kelimeler.contains(" yangın ") || safMetin.contains("alev alev") || safMetin.contains("kundaklandı");
        boolean itfaiyeMudahalesi = kelimeler.contains(" itfaiye ") || kelimeler.contains(" söndürüldü ") || safMetin.contains("dumanlar yükseldi");

        if (yanginEylemi && itfaiyeMudahalesi && !safMetin.contains("ateş açtı") && !kelimeler.contains(" silah ")) {
            return "Yangın";
        }

        boolean maddeVarMi = kelimeler.contains(" uyuşturucu ") || kelimeler.contains(" eroin ") || kelimeler.contains(" kokain ") || kelimeler.contains(" esrar ") || kelimeler.contains(" metamfetamin ") || kelimeler.contains(" bonzai ");
        if (maddeVarMi && (kelimeler.contains(" operasyon ") || safMetin.contains("ele geçirildi") || kelimeler.contains(" yakalandı ") || kelimeler.contains(" gözaltına "))) {
            return "Uyuşturucu";
        }


        boolean hirsizlikEylemi = kelimeler.contains(" hırsız ") || kelimeler.contains(" hırsızlık ") || kelimeler.contains(" soygun ") || kelimeler.contains(" gasp ") || safMetin.contains("çelik kasa");
        boolean calmaEylemi = safMetin.contains(" çaldı ") || kelimeler.contains(" çalındı ");

        if (hirsizlikEylemi || calmaEylemi) {
            return "Hırsızlık";
        }


        if (kelimeler.contains(" elektrik ") && kelimeler.contains(" kesintisi ")) {
            return "Elektrik Kesintisi";
        }


        if (kelimeler.contains(" konser ") || kelimeler.contains(" tiyatro ") || kelimeler.contains(" festival ") || kelimeler.contains(" sergi ")) {
            return "Kültürel Etkinlikler";
        }

        return null;
    }
}