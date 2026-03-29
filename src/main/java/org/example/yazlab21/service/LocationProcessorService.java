package org.example.yazlab21.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProcessorService {

    private final GeocodingService geocodingService;
    private final SimilarityService similarityService;
    private final HaberRepository haberRepository;

    private static final double SIMILARITY_THRESHOLD = 0.90;

    /**
     * Haberin konumunu çıkarır, geocoding yapar ve gerekli alanları günceller
     */
    public Haber processLocationForNews(Haber haber) {
        if (haber == null) {
            return null;
        }

        try {
            // Haberin metninden konum bilgisini çıkar
            String konumMetni = geocodingService.extractLocationFromText(
                haber.getBaslik() + " " + haber.getIcerik()
            );

            if (konumMetni != null && !konumMetni.isEmpty()) {
                haber.setKonumMetni(konumMetni);
                log.info("Haberen konum çıkarıldı: {} - {}", haber.getBaslik(), konumMetni);

                // Benzer haberlerdeki en spesifik konumu bul
                String finalLocation = findMostSpecificLocationForSimilarNews(haber);

                if (finalLocation != null) {
                    // Geocoding yap
                    GeocodingService.LocationCoordinates coords = 
                        geocodingService.getKocaeliLocationCoordinates(finalLocation);

                    if (coords != null && coords.isValid()) {
                        haber.setEnlem(coords.latitude);
                        haber.setBoylam(coords.longitude);
                        haber.setKonumMetni(finalLocation);
                        
                        log.info("Konum geocoded: {} => ({}, {})", 
                            finalLocation, coords.latitude, coords.longitude);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Konum işlemesi sırasında hata: {}", e.getMessage(), e);
        }

        return haber;
    }

    /**
     * Benzer haberleri bulur ve en spesifik konum bilgisini seçer
     * %90 ve üzeri benzerlik oranına sahip haberlerde konum bulunur
     */
    public String findMostSpecificLocationForSimilarNews(Haber haber) {
        try {
            // Veritabanında benzer haberleri bul
            List<Haber> allNews = haberRepository.findByHaberTuru(haber.getHaberTuru());

            // Benzer haberleri filtrele (%90+ benzerlik)
            List<Haber> similarNews = allNews.stream()
                .filter(h -> !h.getId().equals(haber.getId()))
                .filter(h -> similarityService.calculateNewsItemSimilarity(
                    haber.getBaslik(), h.getBaslik()) >= SIMILARITY_THRESHOLD
                )
                .collect(Collectors.toList());

            if (similarNews.isEmpty()) {
                // Benzer haber yoksa, sadece bu haberin konumunu kullan
                return haber.getKonumMetni();
            }

            // Benzer haberlerdeki tüm konumları topla
            List<String> allLocations = similarNews.stream()
                .map(Haber::getKonumMetni)
                .filter(loc -> loc != null && !loc.isEmpty())
                .collect(Collectors.toList());

            // Bu haberin konumunu da ekle
            if (haber.getKonumMetni() != null && !haber.getKonumMetni().isEmpty()) {
                allLocations.add(haber.getKonumMetni());
            }

            if (!allLocations.isEmpty()) {
                // En spesifik konumu seç
                String mostSpecific = similarityService.findMostSpecificLocation(allLocations);
                log.info("Benzer {} haberden en spesifik konum seçildi: {}",
                    similarNews.size(), mostSpecific);
                return mostSpecific;
            }

        } catch (Exception e) {
            log.error("Benzer haber aranırken hata: {}", e.getMessage(), e);
        }

        return haber.getKonumMetni();
    }

    /**
     * Tüm haberlerin konumlarını işler (batch işlem)
     */
    public void processAllNewsLocations() {
        log.info("Tüm haberlerin konumları işlenmeye başlandı...");

        try {
            List<Haber> allNews = haberRepository.findAll();
            
            int processed = 0;
            for (Haber haber : allNews) {
                if ((haber.getEnlem() == null || haber.getEnlem() == 0) &&
                    (haber.getBoylam() == null || haber.getBoylam() == 0)) {
                    
                    Haber processedHaber = processLocationForNews(haber);
                    haberRepository.save(processedHaber);
                    processed++;
                }
            }

            log.info("Konum işlemesi tamamlandı. {} haber işlendi.", processed);

        } catch (Exception e) {
            log.error("Toplu konum işlemesi sırasında hata: {}", e.getMessage(), e);
        }
    }

    /**
     * Belirli bir kategori için haberlerin konumlarını işler
     */
    public void processLocationsByCategory(String haberTuru) {
        log.info("'{}' kategorisinin haberleri işlenmeye başlandı...", haberTuru);

        try {
            List<Haber> categoryNews = haberRepository.findByHaberTuru(haberTuru);
            
            int processed = 0;
            for (Haber haber : categoryNews) {
                if ((haber.getEnlem() == null || haber.getEnlem() == 0) &&
                    (haber.getBoylam() == null || haber.getBoylam() == 0)) {
                    
                    Haber processedHaber = processLocationForNews(haber);
                    haberRepository.save(processedHaber);
                    processed++;
                }
            }

            log.info("'{}' kategorisinin konum işlemesi tamamlandı. {} haber işlendi.", 
                haberTuru, processed);

        } catch (Exception e) {
            log.error("Kategori konum işlemesi sırasında hata: {}", e.getMessage(), e);
        }
    }

    /**
     * Konum bilgisine sahip haberleri getirir (harita için)
     */
    public List<Haber> getNewsWithLocations() {
        return haberRepository.findAll().stream()
            .filter(h -> h.getEnlem() != null && h.getEnlem() != 0
                      && h.getBoylam() != null && h.getBoylam() != 0)
            .collect(Collectors.toList());
    }

    /**
     * Kategoriye göre konum bilgisine sahip haberleri getirir
     */
    public List<Haber> getNewsWithLocationsByCategory(String haberTuru) {
        return haberRepository.findByHaberTuru(haberTuru).stream()
            .filter(h -> h.getEnlem() != null && h.getEnlem() != 0
                      && h.getBoylam() != null && h.getBoylam() != 0)
            .collect(Collectors.toList());
    }

    /**
     * DEBUG: Tüm haberleri getirir (konum olup olmadığı farketmez)
     */
    public List<Haber> getAllNews() {
        return haberRepository.findAll();
    }

    /**
     * Haberi sil
     */
    public void deleteNews(Haber haber) {
        haberRepository.delete(haber);
    }

    /**
     * İki haber başlığının benzerliğini hesapla
     */
    public double calculateNewsSimilarity(String title1, String title2) {
        return similarityService.calculateSimilarity(title1, title2);
    }

    /**
     * Konum metninin detay düzeyini puanlar.
     * Mahalle, sokak, cadde gibi ifadeler içeriyorsa puanı artar.
     * Puan eşitliğinde metnin uzunluğu da detayı belirler.
     */
    private int getSpecificityScore(String location) {
        if (location == null || location.trim().isEmpty()) return 0;
        int score = 0;
        String locLower = location.toLowerCase();
        
        if (locLower.contains("mahalle") || locLower.contains("mah.")) score += 100;
        if (locLower.contains("sokak") || locLower.contains("sok.")) score += 100;
        if (locLower.contains("cadde") || locLower.contains("cad.")) score += 100;
        if (locLower.contains("bulvar")) score += 100;
        if (locLower.contains("mevki")) score += 50;
        if (locLower.contains("ilçe") || locLower.contains("merkez")) score += 20;
        
        // Metin uzunluğu ikincil bir detay kriteridir (kısa olan = daha az detay)
        score += location.length();
        
        return score;
    }

    /**
     * Benzer haberleri ayrıştırır:
     * - Başlık ve İçerik %90+ benzerse AYNI olay kabul eder.
     * - Daha spesifik konuma sahip olanı tutar (cadde, mahalle), diğerini siler.
     * - Konumlar aynı detay seviyesindeyse YENİ EKLENENİ siler (eskisini tutar).
     */
    public void deduplicateAndKeepMostSpecific() {
        try {
            List<Haber> allNews = haberRepository.findAll();
            java.util.Set<String> toDeleteIds = new java.util.HashSet<>();
            int removedCount = 0;
            
            log.info("🔍 Benzer haberler analiz ediliyor (Spesifik konum tutulacak, aynıysa yeni olan silinecek)...");
            
            for (int i = 0; i < allNews.size(); i++) {
                Haber haber1 = allNews.get(i);
                if (toDeleteIds.contains(haber1.getId())) continue;
                
                for (int j = i + 1; j < allNews.size(); j++) {
                    Haber haber2 = allNews.get(j);
                    if (toDeleteIds.contains(haber2.getId())) continue;
                    
                    // 1. Başlık ve İçerik benzerliğini kontrol et
                    double titleSimilarity = similarityService.calculateSimilarity(haber1.getBaslik(), haber2.getBaslik());
                    double contentSimilarity = similarityService.calculateSimilarity(haber1.getIcerik(), haber2.getIcerik());
                    boolean sameCategory = haber1.getHaberTuru() != null && haber1.getHaberTuru().equals(haber2.getHaberTuru());
                    
                    // %90+ başlık ve %90+ içerik benzerliği = AYNI OLAY
                    if (titleSimilarity >= 0.90 && contentSimilarity >= 0.90 && sameCategory) {
                        
                        // Konum spesifiklik skorlarını hesapla
                        int score1 = getSpecificityScore(haber1.getKonumMetni());
                        int score2 = getSpecificityScore(haber2.getKonumMetni());
                        
                        Haber keep;
                        Haber remove;
                        String reason;
                        
                        if (score1 > score2) {
                            keep = haber1;
                            remove = haber2;
                            reason = "Daha Spesifik Konum";
                        } else if (score2 > score1) {
                            keep = haber2;
                            remove = haber1;
                            reason = "Daha Spesifik Konum";
                        } else {
                            // Aynı düzeydeyse yeni ekleneni sil (tarihi daha yeni olanı siler)
                            if (haber1.getYayinTarihi() != null && haber2.getYayinTarihi() != null) {
                                if (haber2.getYayinTarihi().isAfter(haber1.getYayinTarihi())) {
                                    keep = haber1; 
                                    remove = haber2;
                                } else {
                                    keep = haber2; 
                                    remove = haber1;
                                }
                            } else {
                                // Tarih kıyaslaması yapılamıyorsa 2. haberi sil
                                keep = haber1; 
                                remove = haber2;
                            }
                            reason = "Aynı Konum Düzeyi (Eski Olan Tutuldu)";
                        }
                        
                        toDeleteIds.add(remove.getId());
                        log.info("✂️ SILINDI: '{}' | TUTULAN: '{}' | SEBEP: {}", 
                                remove.getKonumMetni(), 
                                keep.getKonumMetni(),
                                reason);
                    }
                }
            }
            
            // Seçilenleri veritabanından sil
            for (Haber h : allNews) {
                if (toDeleteIds.contains(h.getId())) {
                    haberRepository.delete(h);
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                log.info("✅ Toplam {} adet benzer haber (daha az spesifik veya yeni olan) silindi.", removedCount);
            } else {
                log.info("ℹ️ Silinecek benzer haber bulunamadı.");
            }

        } catch (Exception e) {
            log.error("Benzerlik ayrıştırma sırasında hata: {}", e.getMessage(), e);
        }
    }
}
