package org.example.yazlab21.controller;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.scraper.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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


    @GetMapping("/bizimyaka")
    public ResponseEntity<String> tetiklebizimYaka() {
        bizimYakaScraper.scrapeBizimYaka();
        return ResponseEntity.ok("Bizim Yaka scraping işlemi tamamlandı. MongoDB'ye bakabilirsin!");
    }

    @GetMapping("/ozgurkocaeli")
    public ResponseEntity<String> tetikleozgurkocaeli() {
        ozgurKocaeliScraper.scrapeOzgurKocaeli();
        return ResponseEntity.ok("Özgür Kocaeli scraping işlemi tamamlandı. MongoDB'ye bakabilirsin!");
    }

    @GetMapping("/cagdaskocaeli")
    public ResponseEntity<String> tetiklecagdaskocaeli() {
        cagdasKocaeliScraper.scrapeCagdasKocaeli();
        return ResponseEntity.ok("Çağdaş Kocaeli scraping işlemi tamamlandı. MongoDB'ye bakabilirsin!");
    }

    @GetMapping("/seskoaceli")
    public ResponseEntity<String> tetikleseskoaceli() {
        sesKocaeliScraper.scrapeSesKocaeli();
        return ResponseEntity.ok("Ses Kocaeli scraping işlemi tamamlandı. MongoDB'ye bakabilirsin!");
    }

    @GetMapping("/yenikocaeli")
    public ResponseEntity<String> tetikleyyenikocaeli() {
        yenikocaeliScraper.scrapeYenikocaeli();
        return ResponseEntity.ok("Yeni Kocaeli scraping işlemi tamamlandı. MongoDB'ye bakabilirsin!");
    }



}