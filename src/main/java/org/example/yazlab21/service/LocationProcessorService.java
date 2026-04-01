package org.example.yazlab21.service;

import lombok.extern.slf4j.Slf4j;
import org.example.yazlab21.model.Haber;
import org.example.yazlab21.repository.HaberRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LocationProcessorService {

    private final GeocodingService geocodingService;
    private final SimilarityService similarityService;
    private final HaberRepository haberRepository;

    private static final double SIMILARITY_THRESHOLD = 0.90;

    public LocationProcessorService(GeocodingService geocodingService,
                                    SimilarityService similarityService,
                                    HaberRepository haberRepository) {
        this.geocodingService = geocodingService;
        this.similarityService = similarityService;
        this.haberRepository = haberRepository;
    }

    /**
     * Tek bir haberin konum bilgisini işler.
     *
     * Akış:
     *  1. Başlık + içerik üzerinde extractAndGeocodeAll() ile TÜM konumlar çıkarılır.
     *  2. Benzer haberlerden daha spesifik konum varsa önceliklendirilir.
     *  3. Geçerli koordinat bulunan ilk (en spesifik) konum habere yazılır.
     *  4. Hiç koordinat bulunamazsa haber enlem/boylam OLMADAN kaydedilir
     *     → haritada gösterilmez (fallback İzmit KALDIRILDI).
     */
    public Haber processLocationForNews(Haber haber) {
        if (haber == null) return null;

        try {
            String aramaMetni = buildSearchText(haber);

            // YENİ: Tüm konumları geocoding ile birlikte getir
            List<GeocodingService.LocationCoordinates> tumKonumlar =
                    geocodingService.extractAndGeocodeAll(aramaMetni);

            if (tumKonumlar.isEmpty()) {
                log.warn("⚠️  Konum bulunamadı, haber haritada gösterilmeyecek: '{}'", haber.getBaslik());
                // Enlem/boylam sıfırlanır → haritada gizlenir
                clearCoordinates(haber);
                haber.setKonumMetni(null);
                return haber;
            }

            // Benzer haberlerin konumlarıyla en spesifik olanı seç
            GeocodingService.LocationCoordinates secilenKonum =
                    findMostSpecificCoordinate(haber, tumKonumlar);

            // Habere yaz
            haber.setKonumMetni(secilenKonum.locationName);
            haber.setEnlem(secilenKonum.latitude);
            haber.setBoylam(secilenKonum.longitude);

            log.info("📍 Konum atandı: '{}' → {} ({}, {})",
                    haber.getBaslik(), secilenKonum.locationName,
                    secilenKonum.latitude, secilenKonum.longitude);

        } catch (Exception e) {
            log.error("❌ Konum işlemesi hatası '{}': {}", haber.getBaslik(), e.getMessage(), e);
        }

        return haber;
    }

    /**
     * Arama metnini oluşturur. Başlık iki kez eklenir çünkü
     * başlıkta geçen konum adı genellikle daha güvenilirdir ve
     * regex eşleşme olasılığını artırır.
     */
    private String buildSearchText(Haber haber) {
        String baslik = haber.getBaslik() != null ? haber.getBaslik() : "";
        String icerik = haber.getIcerik() != null ? haber.getIcerik() : "";
        // Başlığı öne al ve iki kez geçir → ağırlık kazansın
        return baslik + " " + baslik + " " + icerik;
    }

    /**
     * Mevcut haberden çıkarılan koordinat listesi + benzer haberlerin
     * konumlarını birleştirerek en spesifik olanı seçer.
     */
    private GeocodingService.LocationCoordinates findMostSpecificCoordinate(
            Haber haber,
            List<GeocodingService.LocationCoordinates> mevcutKonumlar) {

        try {
            // Benzer haberlerin konum metinlerini topla
            List<Haber> benzerHaberler = haberRepository.findByHaberTuru(haber.getHaberTuru())
                    .stream()
                    .filter(h -> h.getId() != null && !h.getId().equals(haber.getId()))
                    .filter(h -> h.getKonumMetni() != null && !h.getKonumMetni().isBlank())
                    .filter(h -> similarityService.calculateNewsItemSimilarity(
                            haber.getBaslik(), h.getBaslik()) >= SIMILARITY_THRESHOLD)
                    .collect(Collectors.toList());

            if (!benzerHaberler.isEmpty()) {
                // Benzer haberlerden koordinatı olan ve spesifik olanı bul
                Optional<GeocodingService.LocationCoordinates> benzerKonum = benzerHaberler.stream()
                        .filter(h -> hasCoordinates(h))
                        .map(h -> new GeocodingService.LocationCoordinates(
                                h.getKonumMetni(), h.getEnlem(), h.getBoylam(), h.getKonumMetni()))
                        .max(Comparator.comparingInt(k -> getSpecificityScore(k.locationName)));

                if (benzerKonum.isPresent()) {
                    int benzerScore = getSpecificityScore(benzerKonum.get().locationName);
                    int mevcutBest = mevcutKonumlar.stream()
                            .mapToInt(k -> getSpecificityScore(k.locationName))
                            .max().orElse(0);

                    if (benzerScore > mevcutBest) {
                        log.info("🔗 Benzer haberden daha spesifik konum alındı: '{}'",
                                benzerKonum.get().locationName);
                        return benzerKonum.get();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Benzer haber konumu aramasında hata: {}", e.getMessage());
        }

        // Mevcut konumlardan en spesifik olanı döndür
        return mevcutKonumlar.stream()
                .max(Comparator.comparingInt(k -> getSpecificityScore(k.locationName)))
                .orElse(mevcutKonumlar.get(0));
    }

    /**
     * Tüm haberlerin konum bilgisini yeniden işler (force update).
     */
    public void processAllNewsLocations() {
        log.info("🔄 Tüm haberlerin konumları yeniden işleniyor...");
        try {
            List<Haber> allNews = haberRepository.findAll();
            int processed = 0, skipped = 0;

            for (Haber haber : allNews) {
                Haber result = processLocationForNews(haber);
                if (result != null) {
                    haberRepository.save(result);
                    if (hasCoordinates(result)) processed++;
                    else skipped++;
                }
            }

            log.info("✅ Konum işlemesi bitti. Koordinat atanan: {}, Konum bulunamayan: {}",
                    processed, skipped);
        } catch (Exception e) {
            log.error("Toplu konum işlemesi hatası: {}", e.getMessage(), e);
        }
    }

    /**
     * Sadece koordinatsız haberleri işler (kategori bazlı).
     */
    public void processLocationsByCategory(String haberTuru) {
        log.info("📂 '{}' kategorisi işleniyor...", haberTuru);
        try {
            List<Haber> categoryNews = haberRepository.findByHaberTuru(haberTuru);
            int processed = 0;

            for (Haber haber : categoryNews) {
                if (!hasCoordinates(haber)) {
                    Haber result = processLocationForNews(haber);
                    if (result != null) {
                        haberRepository.save(result);
                        if (hasCoordinates(result)) processed++;
                    }
                }
            }

            log.info("'{}' kategorisi tamamlandı. Koordinat atanan: {} haber.", haberTuru, processed);
        } catch (Exception e) {
            log.error("Kategori konum işlemesi hatası: {}", e.getMessage(), e);
        }
    }

    // ─── Sorgulama Metotları ────────────────────────────────────────────────

    /** Sadece koordinatı olan haberleri döndürür (harita için). */
    public List<Haber> getNewsWithLocations() {
        return haberRepository.findAll().stream()
                .filter(this::hasCoordinates)
                .collect(Collectors.toList());
    }

    /** Kategori + koordinat filtreli liste. */
    public List<Haber> getNewsWithLocationsByCategory(String haberTuru) {
        return haberRepository.findByHaberTuru(haberTuru).stream()
                .filter(this::hasCoordinates)
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

    // ─── Spesifiklik Skoru ──────────────────────────────────────────────────

    /**
     * Konum metninin ne kadar spesifik olduğunu puanlar.
     * Yüksek puan = daha spesifik = tercih edilir.
     *
     * Mahalle/Sokak/Cadde > Bulvar/Yol > İlçe/Merkez
     */
    public int getSpecificityScore(String location) {
        if (location == null || location.trim().isEmpty()) return 0;
        int score = 0;
        String loc = location.toLowerCase();

        if (loc.contains("mahalle") || loc.contains("mah."))  score += 100;
        if (loc.contains("sokak")   || loc.contains("sok."))  score += 100;
        if (loc.contains("cadde")   || loc.contains("cad."))  score += 100;
        if (loc.contains("bulvar"))                            score += 80;
        if (loc.contains("sanayi"))                            score += 70;
        if (loc.contains("mevki"))                             score += 60;
        if (loc.contains("yolu")    || loc.contains("yol"))   score += 50;
        if (loc.contains("köy"))                               score += 40;
        if (loc.contains("ilçe")    || loc.contains("merkez"))score += 20;

        // Uzun isimler genelde daha spesifiktir
        score += location.length();

        return score;
    }

    // ─── Deduplication ──────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledDeduplication() {
        log.info("⏰ Otomatik mükerrer haber temizliği başlatılıyor...");
        deduplicateAndKeepMostSpecific();
    }

    public void deduplicateAndKeepMostSpecific() {
        try {
            List<Haber> allNews = haberRepository.findAll();
            Set<String> toDeleteIds = new HashSet<>();
            int removedCount = 0;

            log.info("🔍 Benzer haberler analiz ediliyor ({} haber)...", allNews.size());

            for (int i = 0; i < allNews.size(); i++) {
                Haber haber1 = allNews.get(i);
                if (toDeleteIds.contains(haber1.getId())) continue;

                for (int j = i + 1; j < allNews.size(); j++) {
                    Haber haber2 = allNews.get(j);
                    if (toDeleteIds.contains(haber2.getId())) continue;

                    // Farklı kategorideki haberler karşılaştırılmaz
                    if (haber1.getHaberTuru() == null ||
                            !haber1.getHaberTuru().equals(haber2.getHaberTuru())) continue;

                    String b1 = normalize(haber1.getBaslik());
                    String b2 = normalize(haber2.getBaslik());

                    double titleLevenshtein = similarityService.calculateSimilarity(
                            haber1.getBaslik(), haber2.getBaslik());
                    double titleCosine = similarityService.calculateCosineSimilarity(
                            haber1.getBaslik(), haber2.getBaslik());
                    double contentSimilarity = similarityService.calculateSimilarity(
                            normalizeContent(haber1.getIcerik()),
                            normalizeContent(haber2.getIcerik()));

                    boolean isSameEvent =
                            titleCosine >= 0.50 ||
                                    titleLevenshtein >= 0.50 ||
                                    (titleCosine >= 0.40 && contentSimilarity >= 0.40) ||
                                    (b1.length() > 5 && b2.length() > 5 && (b1.contains(b2) || b2.contains(b1)));

                    // Özel durum: trafik kazası eşleşmeleri
                    if (!isSameEvent && "Trafik Kazası".equals(haber1.getHaberTuru())) {
                        isSameEvent = checkSpecialTrafficAccident(haber1, haber2, b1, b2);
                    }

                    if (isSameEvent) {
                        DedupDecision decision = pickKeepAndMerge(haber1, haber2);
                        if (decision.updatedKeep()) haberRepository.save(decision.keep());
                        toDeleteIds.add(decision.remove().getId());

                        log.info("✂️ Silindi: '{}' | Tutuldu: '{}' | Sebep: {}",
                                decision.remove().getBaslik(),
                                decision.keep().getBaslik(),
                                decision.reason());
                    }
                }
            }

            for (Haber h : allNews) {
                if (toDeleteIds.contains(h.getId())) {
                    haberRepository.delete(h);
                    removedCount++;
                }
            }

            log.info(removedCount > 0
                            ? "✅ {} mükerrer haber silindi." : "ℹ️ Silinecek haber bulunamadı.",
                    removedCount);

        } catch (Exception e) {
            log.error("Deduplication hatası: {}", e.getMessage(), e);
        }
    }

    private boolean checkSpecialTrafficAccident(Haber h1, Haber h2, String b1, String b2) {
        String i1 = h1.getIcerik() != null ? h1.getIcerik().toLowerCase() : "";
        String i2 = h2.getIcerik() != null ? h2.getIcerik().toLowerCase() : "";

        boolean sivi1 = (b1.contains("başiskele") || i1.contains("başiskele"))
                && (b1.contains("sıvı") || i1.contains("sıvı") || i1.contains("dökül"));
        boolean sivi2 = (b2.contains("başiskele") || i2.contains("başiskele"))
                && (b2.contains("sıvı") || i2.contains("sıvı") || i2.contains("dökül"));
        if (sivi1 && sivi2) return true;

        boolean tunel1 = (b1.contains("tünel") || i1.contains("tünel"))
                && (b1.contains("kaza") || i1.contains("kaza") || i1.contains("çarp"));
        boolean tunel2 = (b2.contains("tünel") || i2.contains("tünel"))
                && (b2.contains("kaza") || i2.contains("kaza") || i2.contains("çarp"));
        return tunel1 && tunel2;
    }

    private DedupDecision pickKeepAndMerge(Haber haber1, Haber haber2) {
        double score1 = getLocationQuality(haber1);
        double score2 = getLocationQuality(haber2);

        Haber keep, remove;
        String reason;

        if (score1 >= score2) {
            keep = haber1; remove = haber2;
            reason = "Konum kalitesi: " + score1 + " >= " + score2;
        } else {
            keep = haber2; remove = haber1;
            reason = "Konum kalitesi: " + score2 + " > " + score1;
        }

        // Eşitse daha eski olanı tut
        if (score1 == score2 && haber1.getId().compareTo(haber2.getId()) >= 0) {
            keep = haber2; remove = haber1;
            reason = "Eşit kalite — eski kayıt tutuldu";
        }

        boolean updated = mergeBetterLocationData(keep, remove);
        if (updated) reason += " | Konum verisi aktarıldı";

        return new DedupDecision(keep, remove, reason, updated);
    }

    private boolean mergeBetterLocationData(Haber keep, Haber remove) {
        boolean updated = false;

        int keepScore   = getSpecificityScore(keep.getKonumMetni());
        int removeScore = getSpecificityScore(remove.getKonumMetni());

        boolean keepHasLocation   = keep.getKonumMetni() != null && !keep.getKonumMetni().isBlank();
        boolean removeHasLocation = remove.getKonumMetni() != null && !remove.getKonumMetni().isBlank();

        if (removeHasLocation && (!keepHasLocation || removeScore > keepScore)) {
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

    // ─── Yardımcı Metotlar ──────────────────────────────────────────────────

    private double getLocationQuality(Haber haber) {
        if (haber == null) return 0;
        double base = getSpecificityScore(haber.getKonumMetni());
        if (hasCoordinates(haber)) base += 500;
        return base;
    }

    private boolean hasCoordinates(Haber haber) {
        return haber != null
                && haber.getEnlem() != null && haber.getEnlem() != 0
                && haber.getBoylam() != null && haber.getBoylam() != 0;
    }

    /** Haberin enlem/boylamını sıfırlar (haritada gizlemek için). */
    private void clearCoordinates(Haber haber) {
        haber.setEnlem(null);
        haber.setBoylam(null);
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-zğüşıöç0-9]", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalizeContent(String content) {
        if (content == null) return "";
        String c = content.replaceAll("\\s+", " ").trim();
        return c.length() > 1200 ? c.substring(0, 1200) : c;
    }

    private record DedupDecision(Haber keep, Haber remove, String reason, boolean updatedKeep) {}
}