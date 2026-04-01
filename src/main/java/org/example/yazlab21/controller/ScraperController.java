package org.example.yazlab21.controller;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.scraper.*;
import org.example.yazlab21.service.LocationProcessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<String> tetiklebizimYaka(@RequestParam(defaultValue = "3") int days) {
        bizimYakaScraper.scrapeBizimYaka(days);
        locationProcessorService.processAllNewsLocations(); // Tüm kategoriler için konum/geocode
        locationProcessorService.deduplicateAndKeepMostSpecific();
        return ResponseEntity.ok("✅ Bizim Yaka scraping tamamlandı, tüm haberlerin konumu/geocode'u işlendi ve mükerrerler temizlendi!");
    }

    @GetMapping("/ozgurkocaeli")
    public ResponseEntity<String> tetikleozgurkocaeli(@RequestParam(defaultValue = "3") int days) {
        ozgurKocaeliScraper.scrapeOzgurKocaeli(days);
        locationProcessorService.processAllNewsLocations();
        locationProcessorService.deduplicateAndKeepMostSpecific();
        return ResponseEntity.ok("✅ Özgür Kocaeli scraping tamamlandı, tüm haberlerin konumu/geocode'u işlendi ve mükerrerler temizlendi!");
    }

    @GetMapping("/cagdaskocaeli")
    public ResponseEntity<String> tetiklecagdaskocaeli(@RequestParam(defaultValue = "3") int days) {
        cagdasKocaeliScraper.scrapeCagdasKocaeli(days);
        locationProcessorService.processAllNewsLocations();
        locationProcessorService.deduplicateAndKeepMostSpecific();
        return ResponseEntity.ok("✅ Çağdaş Kocaeli scraping tamamlandı, tüm haberlerin konumu/geocode'u işlendi ve mükerrerler temizlendi!");
    }

    @GetMapping("/seskocaeli")
    public ResponseEntity<String> tetikleseskocaeli(@RequestParam(defaultValue = "3") int days) {
        sesKocaeliScraper.scrapeSesKocaeli(days);
        locationProcessorService.processAllNewsLocations();
        locationProcessorService.deduplicateAndKeepMostSpecific();
        return ResponseEntity.ok("✅ Ses Kocaeli scraping tamamlandı, tüm haberlerin konumu/geocode'u işlendi ve mükerrerler temizlendi!");
    }

    @GetMapping("/yenikocaeli")
    public ResponseEntity<String> tetikleyenikocaeli(@RequestParam(defaultValue = "3") int days) {
        yenikocaeliScraper.scrapeYenikocaeli(days);
        locationProcessorService.processAllNewsLocations();
        locationProcessorService.deduplicateAndKeepMostSpecific();
        return ResponseEntity.ok("✅ Yeni Kocaeli scraping tamamlandı, tüm haberlerin konumu/geocode'u işlendi ve mükerrerler temizlendi!");
    }

    @PostMapping("/all-locations")
    public ResponseEntity<String> processAllLocations() {
        locationProcessorService.processAllNewsLocations();
        return ResponseEntity.ok("✅ Tüm haberlerin konumları işlendi!");
    }



}