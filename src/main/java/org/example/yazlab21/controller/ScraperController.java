package org.example.yazlab21.controller;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.scraper.*;
import org.example.yazlab21.service.LocationProcessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scrape")
@RequiredArgsConstructor
public class ScraperController {

    private final BizimYakaScraper bizimYakaScraper;
    private final OzgurKocaeliScraper ozgurKocaeliScraper;
    private final CagdasKocaeliScraper cagdasKocaeliScraper;
    private final SesKocaeliScraper sesKocaeliScraper;
    private final YenikocaeliScraper yenikocaeliScraper;
    private final LocationProcessorService locationProcessorService;


    @GetMapping("/bizimyaka")
    public ResponseEntity<String> tetiklebizimYaka() {
        bizimYakaScraper.scrapeBizimYaka();
        locationProcessorService.processLocationsByCategory("Yangın");
        locationProcessorService.deduplicateAndKeepMostSpecific(); // ← YENİ BENZERLİK VE SİLME MANTIĞI
        return ResponseEntity.ok("✅ Bizim Yaka scraping tamamlandı ve konumlar işlendi!");
    }

    @GetMapping("/ozgurkocaeli")
    public ResponseEntity<String> tetikleozgurkocaeli() {
        ozgurKocaeliScraper.scrapeOzgurKocaeli();
        locationProcessorService.processLocationsByCategory("Yangın");
        locationProcessorService.deduplicateAndKeepMostSpecific(); // ← YENİ BENZERLİK VE SİLME MANTIĞI
        return ResponseEntity.ok("✅ Özgür Kocaeli scraping tamamlandı ve konumlar işlendi!");
    }

    @GetMapping("/cagdaskocaeli")
    public ResponseEntity<String> tetiklecagdaskocaeli() {
        cagdasKocaeliScraper.scrapeCagdasKocaeli();
        locationProcessorService.processLocationsByCategory("Yangın");
        locationProcessorService.deduplicateAndKeepMostSpecific(); // ← YENİ BENZERLİK VE SİLME MANTIĞI
        return ResponseEntity.ok("✅ Çağdaş Kocaeli scraping tamamlandı ve konumlar işlendi!");
    }

    @GetMapping("/seskoaceli")
    public ResponseEntity<String> tetikleseskoaceli() {
        sesKocaeliScraper.scrapeSesKocaeli();
        locationProcessorService.processLocationsByCategory("Yangın");
        locationProcessorService.deduplicateAndKeepMostSpecific(); // ← YENİ BENZERLİK VE SİLME MANTIĞI
        return ResponseEntity.ok("✅ Ses Kocaeli scraping tamamlandı ve konumlar işlendi!");
    }

    @GetMapping("/yenikocaeli")
    public ResponseEntity<String> tetikleyyenikocaeli() {
        yenikocaeliScraper.scrapeYenikocaeli();
        locationProcessorService.processLocationsByCategory("Yangın");
        locationProcessorService.deduplicateAndKeepMostSpecific(); // ← YENİ BENZERLİK VE SİLME MANTIĞI
        return ResponseEntity.ok("✅ Yeni Kocaeli scraping tamamlandı ve konumlar işlendi!");
    }

    @PostMapping("/all-locations")
    public ResponseEntity<String> processAllLocations() {
        locationProcessorService.processAllNewsLocations();
        return ResponseEntity.ok("✅ Tüm haberlerin konumları işlendi!");
    }



}