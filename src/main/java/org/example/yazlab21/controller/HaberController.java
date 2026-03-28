package org.example.yazlab21.controller;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.example.yazlab21.scraper.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/haberler")
@CrossOrigin(origins = "*") // HTML sayfasından gelen isteklere izin ver
@RequiredArgsConstructor
public class HaberController {

    private final HaberRepository haberRepository;

    // Bütün botlarımızı buraya çağırıyoruz (Spring bunları bizim için hazırlayacak)
    private final YenikocaeliScraper yenikocaeliScraper;
    private final BizimYakaScraper bizimYakaScraper;
    private final OzgurKocaeliScraper ozgurKocaeliScraper;
    private final CagdasKocaeliScraper cagdasKocaeliScraper;
    private final SesKocaeliScraper sesKocaeliScraper;

    // Sadece veritabanındaki haberleri görmek istersen (Tarayıcıya localhost:8080/api/haberler yazarak)
    @GetMapping
    public ResponseEntity<List<Haber>> tumHaberleriGetir() {
        return ResponseEntity.ok(haberRepository.findAll());
    }

    // İşte HTML'deki butonun tetiklediği o meşhur endpoint!
    @PostMapping("/scrape-tetikle")
    public ResponseEntity<String> scraperTetikle() {
        try {
            System.out.println("🚀 =============================================== 🚀");
            System.out.println("🤖 HABER BOTLARI MANUEL OLARAK ATEŞLENDİ!");
            System.out.println("🚀 =============================================== 🚀");

            // Botlar sırayla çalışır. Biri bitmeden diğeri başlamaz (Senkron).

            System.out.println("\n--- 1. YENİ KOCAELİ BAŞLIYOR ---");
            yenikocaeliScraper.scrapeYenikocaeli();

            System.out.println("\n--- 2. BİZİM YAKA BAŞLIYOR ---");
            bizimYakaScraper.scrapeBizimYaka();

            System.out.println("\n--- 3. ÖZGÜR KOCAELİ BAŞLIYOR ---");
            ozgurKocaeliScraper.scrapeOzgurKocaeli();

            System.out.println("\n--- 4. ÇAĞDAŞ KOCAELİ BAŞLIYOR ---");
            cagdasKocaeliScraper.scrapeCagdasKocaeli();

            System.out.println("\n--- 5. SES KOCAELİ BAŞLIYOR ---");
            sesKocaeliScraper.scrapeSesKocaeli();

            System.out.println("\n✅ TÜM SİTELER BAŞARIYLA TARANDI VE VERİTABANINA KAYDEDİLDİ!");
            return ResponseEntity.ok("İşlem Başarılı");

        } catch (Exception e) {
            System.err.println("❌ Botlar çalışırken büyük bir hata oluştu: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }
}