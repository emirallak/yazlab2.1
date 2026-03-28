package org.example.yazlab21.scraper;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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
public class SesKocaeliScraper {

    private final HaberRepository haberRepository;

    public void scrapeSesKocaeli() {
        LocalDateTime ucGunOnce = LocalDateTime.now().minusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
        DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        System.out.println("📅 3 Gün Sınırı: " + ucGunOnce.format(logFormat));
        System.out.println("🤖 Selenium WebDriver Başlatılıyor (Ses Kocaeli)...");

        // 1. Chrome'u Arka Planda Hazırla
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        WebDriver driver = new ChromeDriver(options);

        // 2. TARANACAK KATEGORİLER LİSTESİ (Ses Kocaeli)
        List<String> kategoriLinkleri = Arrays.asList(
                "https://www.seskocaeli.com/arsiv/kocaeli-son-dakika-haberler",
                "https://www.seskocaeli.com/arsiv/kocaeli-perde-arkasi-haberler",
                "https://www.seskocaeli.com/arsiv/kocaeli-asayis-haberleri"
        );

        try {
            for (String baseUrl : kategoriLinkleri) {
                boolean eskiHaberSiniri = false;
                int sayfaNumarasi = 1;

                // 3. SAYFALANDIRMA (1, 2, 3... diye gider)
                while (!eskiHaberSiniri && sayfaNumarasi <= 5) {
                    String url = sayfaNumarasi == 1 ? baseUrl : baseUrl + "/" + sayfaNumarasi;
                    System.out.println("\n🌍 Ses Kocaeli Taranıyor (Sayfa " + sayfaNumarasi + "): " + url);

                    driver.get(url);
                    Thread.sleep(5000);

                    Document document = Jsoup.parse(driver.getPageSource());
                    Elements haberKartlari = document.select(".post");

                    if (haberKartlari.isEmpty()) {
                        System.out.println("⏩ Sayfada haber bulunamadı veya Cloudflare aşılamadı.");
                        break;
                    }

                    System.out.println("📊 Sayfada " + haberKartlari.size() + " haber kartı bulundu.");

                    for (Element kart : haberKartlari) {
                        Element aTag = kart.select("h3.b a").first();
                        if (aTag == null) continue;

                        String baslik = aTag.text();
                        String link = aTag.absUrl("href");
                        if (!link.startsWith("http")) {
                            link = "https://www.seskocaeli.com" + aTag.attr("href");
                        }
                        String ozet = kart.select("p.cut-2").text();

                        // ZATEN VAR MI KONTROLÜ
                        if (haberRepository.existsByLink(link)) continue;

                        // AKILLI FİLTRE
                        String tur = haberTuruBelirle(baslik, ozet);
                        if (tur == null) continue;

                        // SELENIUM İLE HABERİN İÇİNE GİR
                        try {
                            Thread.sleep(1500);
                            driver.get(link);

                            Document detay = Jsoup.parse(driver.getPageSource());

                            String tarihStr = detay.select("meta[name='datePublished']").attr("content");
                            if (tarihStr.isEmpty()) tarihStr = detay.select("meta[property='article:published_time']").attr("content");
                            if (tarihStr.isEmpty()) tarihStr = detay.select("time").attr("datetime");

                            if (!tarihStr.isEmpty()) {
                                LocalDateTime haberZamani;
                                try {
                                    haberZamani = OffsetDateTime.parse(tarihStr).atZoneSameInstant(ZoneId.of("Europe/Istanbul")).toLocalDateTime();
                                } catch (Exception e) {
                                    haberZamani = LocalDateTime.now();
                                }

                                // 3 Gün Filtresi
                                if (haberZamani.isBefore(ucGunOnce)) {
                                    System.out.println("⏳ [ESKİ HABER SINIRI] " + haberZamani.format(logFormat) + " -> Sonraki kategoriye geçiliyor.");
                                    eskiHaberSiniri = true;
                                    break;
                                }

                                String tamIcerik = detay.select(".article-body, article, .news-content, .post-content, .content").text();
                                if (tamIcerik.isEmpty()) tamIcerik = ozet;

                                // VERİTABANINA KAYIT (Konum olmadan)
                                Haber yeniHaber = Haber.builder()
                                        .baslik(baslik)
                                        .icerik(tamIcerik)
                                        .haberTuru(tur)
                                        .link(link)
                                        .kaynakAd("Ses Kocaeli")
                                        .yayinTarihi(haberZamani)
                                        .build();

                                haberRepository.save(yeniHaber);
                                System.out.println("✅ KAYDEDİLDİ [" + tur + "] : " + baslik);
                            }

                        } catch (Exception e) {
                            System.err.println("❌ Detay çekilemedi: " + link);
                        }
                    }

                    if (eskiHaberSiniri) {
                        break;
                    }

                    sayfaNumarasi++;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Ses Kocaeli Taraması Çöktü: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("🧹 WebDriver başarıyla kapatıldı.");
            }
        }

        System.out.println("\n🏁 Ses Kocaeli işlemi tamamlandı.");
    }

    private String haberTuruBelirle(String baslik, String icerik) {
        String safMetin = (baslik + " " + icerik).toLowerCase(new Locale("tr"));
        String kelimeler = " " + safMetin.replaceAll("[^a-zğüşıöç]", " ") + " ";

        if (safMetin.contains("mahkeme") || safMetin.contains("duruşma") || safMetin.contains("sanık") ||
                safMetin.contains("yargılan") || safMetin.contains("hakim ") || safMetin.contains("dava") ||
                safMetin.contains("cezaev") || safMetin.contains("müebbet") || safMetin.contains("beraat") ||
                kelimeler.contains(" mesaj ") ||
                kelimeler.contains(" maç ") || kelimeler.contains(" şampiyon ") || kelimeler.contains(" turnuva ") ||
                kelimeler.contains(" pkk ") || kelimeler.contains(" fetö ") || kelimeler.contains(" deaş ") ||
                kelimeler.contains(" yatırım ")) {
            return null;
        }

        boolean kazaEylemi = kelimeler.contains(" kaza ") || safMetin.contains("çarpıştı") || safMetin.contains("takla attı") || safMetin.contains("şarampole") || safMetin.contains("devrildi");
        boolean motorluArac = kelimeler.contains(" araç ") || kelimeler.contains(" otomobil ") || kelimeler.contains(" motosiklet ") || kelimeler.contains(" otobüs ") || kelimeler.contains(" kamyon ") || kelimeler.contains(" tır ") || kelimeler.contains(" sürücü ");
        if (kazaEylemi && motorluArac && !safMetin.contains("iş kazası")) return "Trafik Kazası";

        boolean yanginEylemi = kelimeler.contains(" yangın ") || safMetin.contains("alev alev") || safMetin.contains("kundaklandı");
        boolean itfaiyeMudahalesi = kelimeler.contains(" itfaiye ") || kelimeler.contains(" söndürüldü ") || safMetin.contains("dumanlar yükseldi") || safMetin.contains("kül oldu");
        if (yanginEylemi && itfaiyeMudahalesi && !safMetin.contains("ateş açtı") && !kelimeler.contains(" silah ")) return "Yangın";

        boolean maddeVarMi = kelimeler.contains(" uyuşturucu ") || kelimeler.contains(" eroin ") || kelimeler.contains(" kokain ") || kelimeler.contains(" esrar ") || kelimeler.contains(" metamfetamin ") || kelimeler.contains(" bonzai ") || kelimeler.contains(" zehir tacir ");
        if (maddeVarMi && (kelimeler.contains(" operasyon ") || safMetin.contains("ele geçirildi") || kelimeler.contains(" yakalandı ") || kelimeler.contains(" gözaltı "))) return "Uyuşturucu";

        boolean hirsizlikEylemi = kelimeler.contains(" hırsız ") || kelimeler.contains(" hırsızlık ") || kelimeler.contains(" soygun ") || kelimeler.contains(" gasp ")  || kelimeler.contains(" yankesici ");
        boolean calmaEylemi = safMetin.contains(" çaldı ") || kelimeler.contains(" çalındı ") || kelimeler.contains(" gasp ");
        if (hirsizlikEylemi || calmaEylemi) return "Hırsızlık";


        boolean elektrikVarMi = safMetin.contains("elektrik") || kelimeler.contains(" sedaş ");
        boolean kesintiDurumu = safMetin.contains("kesinti") || safMetin.contains("kesilecek") ;

        // Veya kelimenin kendisi direkt "elektriksiz" ise hiç uzatmadan kesinti diyebiliriz.
        if ((elektrikVarMi && kesintiDurumu) || safMetin.contains("elektriksiz")) {
            return "Elektrik Kesintisi";
        }

        if (kelimeler.contains(" konser ") || kelimeler.contains(" tiyatro ") || kelimeler.contains(" festival ") || kelimeler.contains(" sergi ") || kelimeler.contains(" kitap fuarı ")) return "Kültürel Etkinlikler";

        return null;
    }
}