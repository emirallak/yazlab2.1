package org.example.yazlab21.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.service.LocationProcessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LocationController {

    private final LocationProcessorService locationProcessorService;


    /**
     * Tüm konum bilgisine sahip haberleri getirir (harita için)
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllNewsWithLocations() {
        try {
            List<Haber> news = locationProcessorService.getNewsWithLocations();
            
            List<LocationDTO> locations = news.stream()
                .map(this::convertToLocationDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(locations);
        } catch (Exception e) {
            log.error("Konum verisi getirilirken hata: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }


    /**
     * Kategoriye göre konum bilgisine sahip haberleri getirir
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<?> getNewsLocationsByCategory(@PathVariable String category) {
        try {
            List<Haber> news = locationProcessorService.getNewsWithLocationsByCategory(category);
            
            List<LocationDTO> locations = news.stream()
                .map(this::convertToLocationDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(locations);
        } catch (Exception e) {
            log.error("Kategori konum verisi getirilirken hata: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    /**
     * Tüm haberlerin konumlarını işler (batch)
     */
    @PostMapping("/process-all")
    public ResponseEntity<?> processAllLocations() {
        try {
            locationProcessorService.processAllNewsLocations();
            return ResponseEntity.ok("Tüm haberlerin konumları işlendi.");
        } catch (Exception e) {
            log.error("Batch konum işlemesi sırasında hata: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    /**
     * Kategoriye göre haberlerin konumlarını işler
     */
    @PostMapping("/process-category/{category}")
    public ResponseEntity<?> processLocationsByCategory(@PathVariable String category) {
        try {
            locationProcessorService.processLocationsByCategory(category);
            return ResponseEntity.ok("'" + category + "' kategorisinin haberleri işlendi.");
        } catch (Exception e) {
            log.error("Kategori konum işlemesi sırasında hata: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    /**
     * Harita HTML sayfasını sunar
     */
    @GetMapping("/map")
    public String getMap() {
        return "map";
    }


    /**
     * Benzer haberleri spesifiklik düzeyine göre siler (Yeni mantık)
     */
    @PostMapping("/apply-specific-locations")
    public ResponseEntity<?> applySpecificLocations() {
        try {
            locationProcessorService.deduplicateAndKeepMostSpecific();
            return ResponseEntity.ok(new CleanupResult(
                "Benzer haberler analiz edildi, daha az spesifik/yeni olanlar silindi!"
            ));
        } catch (Exception e) {
            log.error("Konum uygulaması sırasında hata: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    // DTO Sınıfları
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class LocationDTO {
        private String id;
        private String baslik;
        private String icerik;
        private String konumMetni;
        private Double enlem;
        private Double boylam;
        private String yayinTarihi;
        private String link;
        private String kategori;
    }


    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CleanupResult {
        private String message;
    }


    /**
     * Haber'i LocationDTO'ya çevirici
     */
    private LocationDTO convertToLocationDTO(Haber haber) {
        return new LocationDTO(
            haber.getId(),
            haber.getBaslik(),
            haber.getIcerik().length() > 200 ? haber.getIcerik().substring(0, 200) + "..." : haber.getIcerik(),
            haber.getKonumMetni(),
            haber.getEnlem(),
            haber.getBoylam(),
            haber.getYayinTarihi() != null ? haber.getYayinTarihi().toString() : "",
            haber.getLink(),
            haber.getHaberTuru()
        );
    }

    /**
     * DEBUG: Konum bilgisi olmayan haberleri göster
     */
    @GetMapping("/debug/missing-locations")
    public ResponseEntity<?> getMissingLocations() {
        try {
            List<Haber> allNews = locationProcessorService.getAllNews();
            
            List<Haber> missingLocations = allNews.stream()
                .filter(h -> h.getEnlem() == null || h.getEnlem() == 0 
                         || h.getBoylam() == null || h.getBoylam() == 0)
                .collect(Collectors.toList());

            return ResponseEntity.ok(new DebugResult(
                allNews.size(),
                allNews.size() - missingLocations.size(),
                missingLocations.size(),
                missingLocations.stream()
                    .map(this::convertToLocationDTO)
                    .collect(Collectors.toList())
            ));
        } catch (Exception e) {
            log.error("Debug hatası: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    /**
     * DEBUG: Kategoriye göre konum istatistiği
     */
    @GetMapping("/debug/stats-by-category")
    public ResponseEntity<?> getStatsByCategory() {
        try {
            List<Haber> allNews = locationProcessorService.getAllNews();
            
            Map<String, Map<String, Integer>> stats = new java.util.HashMap<>();
            
            for (Haber haber : allNews) {
                String category = haber.getHaberTuru() != null ? haber.getHaberTuru() : "Genel";
                boolean hasLocation = haber.getEnlem() != null && haber.getEnlem() != 0 
                                   && haber.getBoylam() != null && haber.getBoylam() != 0;
                
                stats.putIfAbsent(category, new java.util.HashMap<>());
                stats.get(category).put("toplam", stats.get(category).getOrDefault("toplam", 0) + 1);
                if (hasLocation) {
                    stats.get(category).put("konumlu", stats.get(category).getOrDefault("konumlu", 0) + 1);
                }
            }
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Debug stats hatası: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    /**
     * DEBUG: Konum tekstleri göster
     */
    @GetMapping("/debug/location-texts")
    public ResponseEntity<?> getLocationTexts() {
        try {
            List<Haber> allNews = locationProcessorService.getAllNews();
            
            List<Map<String, Object>> texts = allNews.stream()
                .map(h -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("baslik", h.getBaslik());
                    map.put("konumMetni", h.getKonumMetni());
                    map.put("enlem", h.getEnlem());
                    map.put("boylam", h.getBoylam());
                    map.put("kategori", h.getHaberTuru());
                    return map;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(texts);
        } catch (Exception e) {
            log.error("Debug location texts hatası: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    // DEBUG DTO
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DebugResult {
        private int toplamHaber;
        private int konumluHaber;
        private int konumsuzHaber;
        private List<LocationDTO> konumsuzHaberler;
    }
}
