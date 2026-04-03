package org.example.yazlab21.controller;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.example.yazlab21.scraper.*;
import org.example.yazlab21.service.LocationProcessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/haberler")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class HaberController {

    private final HaberRepository haberRepository;
    private final LocationProcessorService locationProcessorService;



    private final YenikocaeliScraper yenikocaeliScraper;
    private final BizimYakaScraper bizimYakaScraper;
    private final OzgurKocaeliScraper ozgurKocaeliScraper;
    private final CagdasKocaeliScraper cagdasKocaeliScraper;
    private final SesKocaeliScraper sesKocaeliScraper;


    @GetMapping
    public ResponseEntity<List<Haber>> tumHaberleriGetir() {
        return ResponseEntity.ok(haberRepository.findAll());
    }


    @PostMapping("/scrape-tetikle")
    public ResponseEntity<String> scraperTetikle(@RequestParam(defaultValue = "3") int days) {
        try {
            System.out.println("=============================================== ");
            System.out.println(" HABER BOTLARI MANUEL OLARAK ATEŞLENDİ!");
            System.out.println("=============================================== ");



            System.out.println("\n--- 1. YENİ KOCAELİ BAŞLIYOR ---");
            yenikocaeliScraper.scrapeYenikocaeli(days);

            System.out.println("\n--- 2. BİZİM YAKA BAŞLIYOR ---");
            bizimYakaScraper.scrapeBizimYaka(days);

            System.out.println("\n--- 3. ÖZGÜR KOCAELİ BAŞLIYOR ---");
            ozgurKocaeliScraper.scrapeOzgurKocaeli(days);

            System.out.println("\n--- 4. ÇAĞDAŞ KOCAELİ BAŞLIYOR ---");
            cagdasKocaeliScraper.scrapeCagdasKocaeli(days);

            System.out.println("\n--- 5. SES KOCAELİ BAŞLIYOR ---");
            sesKocaeliScraper.scrapeSesKocaeli(days);

            System.out.println("\n✅ TÜM SİTELER BAŞARIYLA TARANDI VE VERİTABANINA KAYDEDİLDİ!");
            

            System.out.println("\n🔍 BENZER HABERLER İÇİN KONUM SPESİFİKLİK KONTROLÜ YAPILIYOR...");
            locationProcessorService.deduplicateAndKeepMostSpecific();
            System.out.println("✅ İŞLEM TAMAMLANDI!\n");
            
            return ResponseEntity.ok("İşlem Başarılı");

        } catch (Exception e) {
            System.err.println("Botlar çalışırken büyük bir hata oluştu: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }
}