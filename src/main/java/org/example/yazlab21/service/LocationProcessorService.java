package org.example.yazlab21.service;

import lombok.extern.slf4j.Slf4j;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LocationProcessorService {

    private final GeocodingService geocodingService;
    private final SimilarityService similarityService;
    private final HaberRepository haberRepository;

    private static final double SIMILARITY_THRESHOLD = 0.90;

    public LocationProcessorService(GeocodingService geocodingService, SimilarityService similarityService, HaberRepository haberRepository) {
        this.geocodingService = geocodingService;
        this.similarityService = similarityService;
        this.haberRepository = haberRepository;
    }

    public Haber processLocationForNews(Haber haber) {
        if (haber == null) {
            return null;
        }

        try {
            String konumMetni = geocodingService.extractLocationFromText(
                    haber.getBaslik() + " " + haber.getIcerik()
            );

            if (konumMetni == null || konumMetni.isEmpty()) {
                konumMetni = "İzmit"; // Merkez varsayımı
            }

            haber.setKonumMetni(konumMetni);
            log.info("Haberden konum çıkarıldı: {} - {}", haber.getBaslik(), konumMetni);

            String finalLocation = findMostSpecificLocationForSimilarNews(haber);

            if (finalLocation != null && !finalLocation.isBlank()) {
                GeocodingService.LocationCoordinates coords =
                        geocodingService.getKocaeliLocationCoordinates(finalLocation);

                if (coords != null && coords.isValid()) {
                    haber.setEnlem(coords.latitude);
                    haber.setBoylam(coords.longitude);
                    haber.setKonumMetni(finalLocation);

                    log.info("Konum geocoded: {} => ({}, {})",
                            finalLocation, coords.latitude, coords.longitude);
                } else {
                    log.warn("Geocode başarısız, varsayılan merkez uygulanacak: {}", finalLocation);
                    applyDefaultCoordinates(haber);
                }
            } else {
                applyDefaultCoordinates(haber);
            }
        } catch (Exception e) {
            log.error("Konum işlemesi sırasında hata: {}", e.getMessage(), e);
        }

        return haber;
    }

    public String findMostSpecificLocationForSimilarNews(Haber haber) {
        try {
            List<Haber> allNews = haberRepository.findByHaberTuru(haber.getHaberTuru());

            List<Haber> similarNews = allNews.stream()
                    .filter(h -> !h.getId().equals(haber.getId()))
                    .filter(h -> similarityService.calculateNewsItemSimilarity(
                            haber.getBaslik(), h.getBaslik()) >= SIMILARITY_THRESHOLD
                    )
                    .collect(Collectors.toList());

            if (similarNews.isEmpty()) {
                return haber.getKonumMetni();
            }

            List<String> allLocations = similarNews.stream()
                    .map(Haber::getKonumMetni)
                    .filter(loc -> loc != null && !loc.isEmpty())
                    .collect(Collectors.toList());

            if (haber.getKonumMetni() != null && !haber.getKonumMetni().isEmpty()) {
                allLocations.add(haber.getKonumMetni());
            }

            if (!allLocations.isEmpty()) {
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

    public void processAllNewsLocations() {
        log.info("Tüm haberlerin konumları ZORUNLU olarak yeniden işlenmeye başlandı...");
        try {
            List<Haber> allNews = haberRepository.findAll();
            int processed = 0;
            for (Haber haber : allNews) {
                // ARTIK ESKİ KOORDİNATI OLSA BİLE YENİDEN İŞLEYECEK (Force Update)
                // Haber zaten kayıtlı, sadece konumunu güncelleyip üstüne yazacağız
                Haber processedHaber = processLocationForNews(haber);
                if (processedHaber != null) {
                    haberRepository.save(processedHaber);
                    processed++;
                }
            }
            log.info("Konum işlemesi tamamlandı. Toplam {} haber yeni API ile güncellendi.", processed);
        } catch (Exception e) {
            log.error("Toplu konum işlemesi sırasında hata: {}", e.getMessage(), e);
        }
    }

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
            log.info("'{}' kategorisinin konum işlemesi tamamlandı. {} haber işlendi.", haberTuru, processed);
        } catch (Exception e) {
            log.error("Kategori konum işlemesi sırasında hata: {}", e.getMessage(), e);
        }
    }

    public List<Haber> getNewsWithLocations() {
        return haberRepository.findAll().stream()
                .filter(h -> h.getEnlem() != null && h.getEnlem() != 0
                        && h.getBoylam() != null && h.getBoylam() != 0)
                .collect(Collectors.toList());
    }

    public List<Haber> getNewsWithLocationsByCategory(String haberTuru) {
        return haberRepository.findByHaberTuru(haberTuru).stream()
                .filter(h -> h.getEnlem() != null && h.getEnlem() != 0
                        && h.getBoylam() != null && h.getBoylam() != 0)
                .collect(Collectors.toList());
    }

    public List<Haber> getAllNews() {
        return haberRepository.findAll();
    }

    public void deleteNews(Haber haber) {
        haberRepository.delete(haber);
    }

    public double calculateNewsSimilarity(String title1, String title2) {
        return similarityService.calculateSimilarity(title1, title2);
    }

    public int getSpecificityScore(String location) {
        if (location == null || location.trim().isEmpty()) return 0;
        int score = 0;
        String locLower = location.toLowerCase();

        if (locLower.contains("mahalle") || locLower.contains("mah.")) score += 100;
        if (locLower.contains("sokak") || locLower.contains("sok.")) score += 100;
        if (locLower.contains("cadde") || locLower.contains("cad.")) score += 100;
        if (locLower.contains("bulvar")) score += 100;
        if (locLower.contains("mevki")) score += 50;
        if (locLower.contains("yolu") || locLower.contains("yol")) score += 50;
        if (locLower.contains("ilçe") || locLower.contains("merkez")) score += 20;

        score += location.length();

        return score;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledDeduplication() {
        log.info("⏰ Otomatik mükerrer haber temizliği başlatılıyor...");
        deduplicateAndKeepMostSpecific();
    }

    public void deduplicateAndKeepMostSpecific() {
        try {
            List<Haber> allNews = haberRepository.findAll();
            java.util.Set<String> toDeleteIds = new java.util.HashSet<>();
            int removedCount = 0;

            log.info("🔍 Farklı sitelerdeki benzer haberler analiz ediliyor...");

            for (int i = 0; i < allNews.size(); i++) {
                Haber haber1 = allNews.get(i);
                if (toDeleteIds.contains(haber1.getId())) continue;

                for (int j = i + 1; j < allNews.size(); j++) {
                    Haber haber2 = allNews.get(j);
                    if (toDeleteIds.contains(haber2.getId())) continue;

                    if (haber1.getHaberTuru() == null || !haber1.getHaberTuru().equals(haber2.getHaberTuru())) {
                        continue;
                    }

                    String b1 = haber1.getBaslik().toLowerCase().replaceAll("[^a-zğüşıöç0-9]", " ").replaceAll("\\s+", " ").trim();
                    String b2 = haber2.getBaslik().toLowerCase().replaceAll("[^a-zğüşıöç0-9]", " ").replaceAll("\\s+", " ").trim();

                    // Hem Levenshtein hem Cosine (kelime bazlı) benzerlik hesaplıyoruz
                    double titleLevenshtein = similarityService.calculateSimilarity(haber1.getBaslik(), haber2.getBaslik());
                    double titleCosine = similarityService.calculateCosineSimilarity(haber1.getBaslik(), haber2.getBaslik());
                    
                    double contentSimilarity = similarityService.calculateSimilarity(
                            normalizeContentForSimilarity(haber1.getIcerik()),
                            normalizeContentForSimilarity(haber2.getIcerik()));

                    boolean isSameEvent = false;

                    // 1. Genel Metin veya Kelime Kesişimi
                    // Cosine similarity kelime sırasından bağımsız olarak benzerliği ölçtüğü için aynı haberi yakalamada çok daha iyidir.
                    if (titleCosine >= 0.50 || titleLevenshtein >= 0.50 || (titleCosine >= 0.40 && contentSimilarity >= 0.40)) {
                        isSameEvent = true;
                    }

                    // 2. Alt Küme Başlık Kontrolü (Biri diğerinin içinde geçiyorsa)
                    // Örn: "Kandıra yolunda kaza!" ile "Kandıra yolunda korkutan kaza: 2 otomobil çarpıştı!"
                    if (!isSameEvent && b1.length() > 5 && b2.length() > 5) {
                        if (b1.contains(b2) || b2.contains(b1)) {
                            isSameEvent = true;
                            log.info("⚠️ BAŞLIK İÇERME YAKALANDI: '{}' <-> '{}'", haber1.getBaslik(), haber2.getBaslik());
                        }
                    }

                    // 3. ÖZEL DURUM: İnatçı Kazalar (Başiskele/Sıvı Dökülmesi vb.)
                    if (!isSameEvent && "Trafik Kazası".equals(haber1.getHaberTuru())) {
                        String i1 = haber1.getIcerik().toLowerCase();
                        String i2 = haber2.getIcerik().toLowerCase();

                        boolean kaza1_basiskele = (b1.contains("başiskele") || i1.contains("başiskele")) && (b1.contains("sıvı") || i1.contains("sıvı") || b1.contains("dökül") || i1.contains("dökül"));
                        boolean kaza2_basiskele = (b2.contains("başiskele") || i2.contains("başiskele")) && (b2.contains("sıvı") || i2.contains("sıvı") || b2.contains("dökül") || i2.contains("dökül"));

                        if (kaza1_basiskele && kaza2_basiskele) {
                            isSameEvent = true;
                            log.info("⚠️ ÖZEL EŞLEŞME YAKALANDI (Başiskele Sıvı Kazası): '{}' ve '{}'", haber1.getBaslik(), haber2.getBaslik());
                        }

                        boolean kaza1_tunel = (b1.contains("tünel") || i1.contains("tünel")) && (b1.contains("kaza") || i1.contains("kaza") || b1.contains("çarp") || i1.contains("çarp"));
                        boolean kaza2_tunel = (b2.contains("tünel") || i2.contains("tünel")) && (b2.contains("kaza") || i2.contains("kaza") || b2.contains("çarp") || i2.contains("çarp"));

                        if (!isSameEvent && kaza1_tunel && kaza2_tunel) {
                            isSameEvent = true;
                            log.info("⚠️ ÖZEL EŞLEŞME YAKALANDI (Tünel Kazası): '{}' ve '{}'", haber1.getBaslik(), haber2.getBaslik());
                        }
                    }

                    if (isSameEvent) {
                        DedupDecision decision = pickKeepAndMerge(haber1, haber2);

                        if (decision.updatedKeep) haberRepository.save(decision.keep);

                        toDeleteIds.add(decision.remove.getId());

                        log.info("✂️ SILINDI ({}): '{}' | TUTULDU ({}): '{}' | SEBEP: {}",
                                decision.remove.getKaynakAd() != null ? decision.remove.getKaynakAd() : "?",
                                decision.remove.getBaslik(),
                                decision.keep.getKaynakAd() != null ? decision.keep.getKaynakAd() : "?",
                                decision.keep.getBaslik(),
                                decision.reason);
                    }
                }
            }

            for (Haber h : allNews) {
                if (toDeleteIds.contains(h.getId())) {
                    haberRepository.delete(h);
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                log.info("✅ Toplam {} adet farklı siteden alınmış mükerrer haber silindi.", removedCount);
            } else {
                log.info("ℹ️ Silinecek benzer haber bulunamadı.");
            }

        } catch (Exception e) {
            log.error("Benzerlik ayrıştırma sırasında hata: {}", e.getMessage(), e);
        }
    }

    private DedupDecision pickKeepAndMerge(Haber haber1, Haber haber2) {
        double score1 = getLocationQuality(haber1);
        double score2 = getLocationQuality(haber2);

        Haber keep;
        Haber remove;
        String reason;

        if (score1 > score2) {
            keep = haber1;
            remove = haber2;
            reason = "Daha spesifik/koordinatlı konum (" + score1 + " > " + score2 + ")";
        } else if (score2 > score1) {
            keep = haber2;
            remove = haber1;
            reason = "Daha spesifik/koordinatlı konum (" + score2 + " > " + score1 + ")";
        } else if (hasCoordinates(haber1) && !hasCoordinates(haber2)) {
            keep = haber1;
            remove = haber2;
            reason = "Koordinat var";
        } else if (hasCoordinates(haber2) && !hasCoordinates(haber1)) {
            keep = haber2;
            remove = haber1;
            reason = "Koordinat var";
        } else {
            // Konum kalitesi eşit: daha eski olanı tut
            if (haber1.getId().compareTo(haber2.getId()) < 0) {
                keep = haber1;
                remove = haber2;
            } else {
                keep = haber2;
                remove = haber1;
            }
            reason = "Aynı konum düzeyi (son eklenen silindi)";
        }

        boolean updated = mergeBetterLocationData(keep, remove);
        if (updated) {
            reason += " | Konum/koordinat aktarıldı";
        }

        return new DedupDecision(keep, remove, reason, updated);
    }

    private boolean mergeBetterLocationData(Haber keep, Haber remove) {
        boolean updated = false;

        int keepScore = getSpecificityScore(keep.getKonumMetni());
        int removeScore = getSpecificityScore(remove.getKonumMetni());

        if ((keep.getKonumMetni() == null || keep.getKonumMetni().isBlank())
                && remove.getKonumMetni() != null && !remove.getKonumMetni().isBlank()) {
            keep.setKonumMetni(remove.getKonumMetni());
            updated = true;
        } else if (removeScore > keepScore && remove.getKonumMetni() != null && !remove.getKonumMetni().isBlank()) {
            keep.setKonumMetni(remove.getKonumMetni());
            updated = true;
        }

        if (!hasCoordinates(keep) && hasCoordinates(remove)) {
            keep.setEnlem(remove.getEnlem());
            keep.setBoylam(remove.getBoylam());
            updated = true;
        }

        return updated;
    }

    private double getLocationQuality(Haber haber) {
        if (haber == null) {
            return 0;
        }

        double base = getSpecificityScore(haber.getKonumMetni());

        if (hasCoordinates(haber)) {
            base += 500; // Koordinatlı kayıtlar kuvvetle tercih edilir
        }

        return base;
    }

    private String normalizeContentForSimilarity(String content) {
        if (content == null) return "";
        String cleaned = content.replaceAll("\\s+", " ").trim();
        int limit = 1200; // Yorum/ilgili haber kalabalığını azaltmak için kısalt
        return cleaned.length() > limit ? cleaned.substring(0, limit) : cleaned;
    }

    private boolean hasCoordinates(Haber haber) {
        return haber != null && haber.getEnlem() != null && haber.getEnlem() != 0
                && haber.getBoylam() != null && haber.getBoylam() != 0;
    }

    private record DedupDecision(Haber keep, Haber remove, String reason, boolean updatedKeep) {}

    private void applyDefaultCoordinates(Haber haber) {
        GeocodingService.LocationCoordinates fallback = geocodingService.getKocaeliLocationCoordinates("İzmit");
        if (fallback != null && fallback.isValid()) {
            haber.setKonumMetni("İzmit");
            haber.setEnlem(fallback.latitude);
            haber.setBoylam(fallback.longitude);
        }
    }
}