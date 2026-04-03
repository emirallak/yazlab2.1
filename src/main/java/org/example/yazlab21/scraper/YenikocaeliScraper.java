package org.example.yazlab21.scraper;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.example.yazlab21.util.ContentExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class YenikocaeliScraper
{

    private final HaberRepository haberRepository;

    public void scrapeYenikocaeli(int days)
    {
        LocalDateTime limitTarih = LocalDateTime.now().minusDays(days);
        DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        System.out.println(" " + days + " Gün Sınırı: " + limitTarih.format(logFormat));
        System.out.println("Jsoup ile Hızlı Tarama Başlatılıyor (Yeni Kocaeli)...");


        List<String> kategoriLinkleri = Arrays.asList("https://www.yenikocaeli.com/kategori/asayis", "https://www.yenikocaeli.com/kategori/gundem");

        for (String baseUrl : kategoriLinkleri)
        {
            boolean eskiHaberSiniri = false;
            int sayfaNumarasi = 1;


            while (!eskiHaberSiniri && sayfaNumarasi <= 5)
            {
                try
                {
                    String url = sayfaNumarasi == 1 ? baseUrl : baseUrl + "/page/" + sayfaNumarasi + "/";
                    System.out.println("\nYeni Kocaeli Taranıyor (Sayfa " + sayfaNumarasi + "): " + url);

                    Document document = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)").timeout(20000).get();


                    Elements haberKartlari = document.select("article, .post-item, .pt-cv-content-item");

                    if (haberKartlari.isEmpty()) {
                        System.out.println("Sayfada haber bulunamadı, diğer kategoriye geçiliyor...");
                        break;
                    }

                    System.out.println("Sayfada " + haberKartlari.size() + " haber kartı bulundu.");

                    for (Element kart : haberKartlari)
                    {
                        Element aTag = kart.select("h2 a, h3 a, .entry-title a").first();
                        if (aTag == null) continue;

                        String baslik = aTag.text();
                        String link = aTag.absUrl("href");
                        String ozet = kart.select(".entry-content, .post-excerpt, p").text();

                        if (haberRepository.existsByLink(link)) continue;

                        String tur = haberTuruBelirle(baslik, ozet);
                        if (tur == null) continue;


                        try {
                            Thread.sleep(500);
                            Document detay = Jsoup.connect(link).userAgent("Mozilla/5.0").timeout(20000).get();


                            String tarihStr = detay.select("meta[property='article:published_time']").attr("content");
                            if (tarihStr.isEmpty()) tarihStr = detay.select("meta[name='datePublished']").attr("content");
                            if (tarihStr.isEmpty()) tarihStr = detay.select("time.entry-date").attr("datetime");

                            if (!tarihStr.isEmpty())
                            {
                                LocalDateTime haberZamani;
                                try
                                {
                                    haberZamani = OffsetDateTime.parse(tarihStr).atZoneSameInstant(ZoneId.of("Europe/Istanbul")).toLocalDateTime();
                                }
                                catch (Exception e)
                                {
                                    haberZamani = LocalDateTime.now();
                                }


                                if (haberZamani.isBefore(limitTarih))
                                {
                                    System.out.println("[ESKİ HABER SINIRI] " + haberZamani.format(logFormat) + " -> Sonraki kategoriye geçiliyor.");
                                    eskiHaberSiniri = true;
                                    break;
                                }

                                String tamIcerik = ContentExtractor.extractMainText(detay, Arrays.asList(".entry-content", ".article-content", ".post-content", "article"), ozet);


                                Haber yeniHaber = Haber.builder().baslik(baslik).icerik(tamIcerik).haberTuru(tur).link(link).kaynakAd("Yeni Kocaeli").yayinTarihi(haberZamani).build();

                                haberRepository.save(yeniHaber);
                                System.out.println("KAYDEDİLDİ [" + tur + "] : " + baslik);
                            }

                        } catch (Exception e) {
                            System.err.println("Detay çekilemedi: " + link);
                        }
                    }

                    if (eskiHaberSiniri) {
                        break;
                    }

                    sayfaNumarasi++;

                } catch (Exception e) {
                    System.err.println("Yeni Kocaeli Sayfa Hatası: " + e.getMessage());
                    break;
                }
            }
        }
        System.out.println("\nYeni Kocaeli işlemi tamamlandı.");
    }


    private String haberTuruBelirle(String baslik, String icerik) {
        String safMetin = (baslik + " " + icerik).toLowerCase(new Locale("tr"));
        String kelimeler = " " + safMetin.replaceAll("[^a-zğüşıöç]", " ") + " ";


        if (safMetin.contains("mahkeme") || safMetin.contains("duruşma") || safMetin.contains("sanık") ||
                safMetin.contains("yargılan") || safMetin.contains("hakim ") || safMetin.contains("dava") ||
                safMetin.contains("cezaev") || safMetin.contains("müebbet") || safMetin.contains("beraat") ||
                kelimeler.contains(" mesaj ") || kelimeler.contains(" kutladı ") || kelimeler.contains(" bayram ") ||
                kelimeler.contains(" maç ") || kelimeler.contains(" şampiyon ") || kelimeler.contains(" turnuva ") ||
                kelimeler.contains(" pkk ") || kelimeler.contains(" fetö ") || kelimeler.contains(" deaş ") ||
                kelimeler.contains(" yatırım ")) {
            return null;
        }

        boolean kazaEylemi = kelimeler.contains(" kaza ") || safMetin.contains("çarpıştı") || safMetin.contains("takla attı") || safMetin.contains("şarampole") || safMetin.contains("devrildi") || safMetin.contains("saplandı") || safMetin.contains("yoldan çıktı");
        boolean motorluArac = kelimeler.contains(" araç ") || kelimeler.contains(" otomobil ") || kelimeler.contains(" motosiklet ") || kelimeler.contains(" otobüs ") || kelimeler.contains(" kamyon ") || kelimeler.contains(" tır ") || kelimeler.contains(" sürücü ");
        if (kazaEylemi && motorluArac && !safMetin.contains("iş kazası")) return "Trafik Kazası";

        boolean yanginEylemi = kelimeler.contains(" yangın ") || safMetin.contains("alev alev") || safMetin.contains("kundaklandı");
        boolean itfaiyeMudahalesi = kelimeler.contains(" itfaiye ") || kelimeler.contains(" söndürüldü ") || safMetin.contains("dumanlar yükseldi") || safMetin.contains("kül oldu");
        if (yanginEylemi && itfaiyeMudahalesi && !safMetin.contains("ateş açtı") && !kelimeler.contains(" silah ")) return "Yangın";



        boolean istisnaDurumu = kelimeler.contains(" eğitim ") ||
                kelimeler.contains(" seminer ") ||
                kelimeler.contains(" buluşma ") ||
                kelimeler.contains(" ziyaret ") ||
                kelimeler.contains(" uyarı ") ||
                kelimeler.contains(" bilgilendirme ") ||
                kelimeler.contains(" konferans ");


        boolean hirsizlikEylemi = kelimeler.contains(" hırsız ") ||
                kelimeler.contains(" hırsızlık ") ||
                kelimeler.contains(" soygun ") ||
                kelimeler.contains(" gasp ")  ||
                kelimeler.contains(" yankesici ");

        boolean calmaEylemi = safMetin.contains(" çaldı ") ||
                kelimeler.contains(" çalındı ") ||
                kelimeler.contains(" gasp ");

        if ((hirsizlikEylemi || calmaEylemi) && !istisnaDurumu)
        {
            return "Hırsızlık";
        }
        if (kelimeler.contains(" elektrik ") && kelimeler.contains(" kesintisi ")) return "Elektrik Kesintisi";

        if (kelimeler.contains(" konser ") || kelimeler.contains(" tiyatro ") || kelimeler.contains(" festival ") || kelimeler.contains(" sergi ") || kelimeler.contains(" kitap fuarı ")|| kelimeler.contains(" etkinlik ")) return "Kültürel Etkinlikler";

        return null;
    }
}