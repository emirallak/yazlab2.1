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
     private static final java.util.regex.Pattern ROAD_CODE_PATTERN = java.util.regex.Pattern.compile(
            "\\b(?:[deo])\\s*-?\\s*\\d{1,4}\\b|\\btem\\b|\\botoyol\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern PLACE_ANCHOR_PATTERN = java.util.regex.Pattern.compile(
            "\\b([a-z0-9çğıöşü]+(?:\\s+[a-z0-9çğıöşü]+){0,2})\\s+"
                    + "(t[üu]neli|t[üu]nelinde|k[öo]pr[üu]s[üu]|k[öo]pr[üu]de|kavsa[gğ][ıi]|kavsakta|"
                    + "mahallesi|mahallede|bulvar[ıi]|bulvarda|caddesi|caddede|soka[gğ][ıi]|sokakta|"
                    + "otoyolu|yolu|il[cç]esi|il[cç]ede|mevkii)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    public LocationProcessorService(GeocodingService geocodingService, SimilarityService similarityService, HaberRepository haberRepository) {
        this.geocodingService = geocodingService;
        this.similarityService = similarityService;
        this.haberRepository = haberRepository;
    }

    public boolean isDuplicateNews(Haber yeniHaber) {
        if (yeniHaber == null || yeniHaber.getBaslik() == null || yeniHaber.getHaberTuru() == null) {
            return false;
        }

        try {
            List<Haber> mevcutHaberler = haberRepository.findByHaberTuru(yeniHaber.getHaberTuru());

            for (Haber mevcut : mevcutHaberler) {
                if (mevcut.getId() != null && mevcut.getId().equals(yeniHaber.getId())) continue;

                if (isSameEventPair(yeniHaber, mevcut)) {
                    log.info("Veritabanına kaydolmadan ENGELLENDİ: '{}' (Mevcut Haber: '{}')",
                            yeniHaber.getBaslik(), mevcut.getBaslik());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Anlık mükerrer kontrolünde hata: {}", e.getMessage());
        }
        return false;
    }

    public Haber processLocationForNews(Haber haber) {
        if (haber == null) return null;

        try {
            String aramaMetni = buildSearchText(haber);

            List<GeocodingService.LocationCoordinates> tumKonumlar =
                    geocodingService.extractAndGeocodeAll(aramaMetni);

            if (tumKonumlar.isEmpty()) {
                log.warn("Konum bulunamadı, haber haritada gösterilmeyecek: '{}'", haber.getBaslik());
                clearCoordinates(haber);
                haber.setKonumMetni(null);
                return haber;
            }

            GeocodingService.LocationCoordinates secilenKonum =
                    findMostSpecificCoordinate(haber, tumKonumlar);

            haber.setKonumMetni(secilenKonum.locationName);
            haber.setEnlem(secilenKonum.latitude);
            haber.setBoylam(secilenKonum.longitude);

            log.info("Konum atandı: '{}' → {} ({}, {})",
                    haber.getBaslik(), secilenKonum.locationName,
                    secilenKonum.latitude, secilenKonum.longitude);

        } catch (Exception e) {
            log.error("Konum işlemesi hatası '{}': {}", haber.getBaslik(), e.getMessage(), e);
        }

        return haber;
    }

    private String buildSearchText(Haber haber) {
        String baslik = haber.getBaslik() != null ? haber.getBaslik() : "";
        String icerik = haber.getIcerik() != null ? haber.getIcerik() : "";
        return baslik + " " + baslik + " " + icerik;
    }

    private GeocodingService.LocationCoordinates findMostSpecificCoordinate(
            Haber haber,
            List<GeocodingService.LocationCoordinates> mevcutKonumlar) {

        try {
            List<Haber> benzerHaberler = haberRepository.findByHaberTuru(haber.getHaberTuru()).stream()
                    .filter(h -> h.getId() != null && !h.getId().equals(haber.getId()))
                    .filter(h -> h.getKonumMetni() != null && !h.getKonumMetni().isBlank())
                    .filter(h -> similarityService.calculateNewsItemSimilarity(haber.getBaslik(), h.getBaslik()) >= SIMILARITY_THRESHOLD)
                    .collect(Collectors.toList());

            if (!benzerHaberler.isEmpty()) {
                Optional<GeocodingService.LocationCoordinates> benzerKonum = benzerHaberler.stream()
                        .filter(this::hasCoordinates)
                        .map(h -> new GeocodingService.LocationCoordinates(h.getKonumMetni(), h.getEnlem(), h.getBoylam(), h.getKonumMetni()))
                        .max(Comparator.comparingInt(k -> getSpecificityScore(k.locationName)));

                if (benzerKonum.isPresent()) {
                    int benzerScore = getSpecificityScore(benzerKonum.get().locationName);
                    int mevcutBest = mevcutKonumlar.stream()
                            .mapToInt(k -> getSpecificityScore(k.locationName))
                            .max().orElse(0);

                    if (benzerScore > mevcutBest) {
                        log.info("Benzer haberden daha spesifik konum alındı: '{}'",
                                benzerKonum.get().locationName);
                        return benzerKonum.get();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Benzer haber konumu aramasında hata: {}", e.getMessage());
        }

        return mevcutKonumlar.stream()
                .max(Comparator.comparingInt(k -> getSpecificityScore(k.locationName)))
                .orElse(mevcutKonumlar.get(0));
    }

    public void processAllNewsLocations() {
        log.info("Tüm haberlerin konumları yeniden işleniyor...");
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

            log.info("Konum işlemesi bitti. Koordinat atanan: {}, Konum bulunamayan: {}", processed, skipped);
        } catch (Exception e) {
            log.error("Toplu konum işlemesi hatası: {}", e.getMessage(), e);
        }
    }

    public void processLocationsByCategory(String haberTuru) {
        log.info(" '{}' kategorisi işleniyor...", haberTuru);
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

    public List<Haber> getNewsWithLocations() {
        return haberRepository.findAll().stream()
                .filter(this::hasCoordinates)
                .collect(Collectors.toList());
    }

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

    public int getSpecificityScore(String location) {
        if (location == null || location.trim().isEmpty())
            return 0;
        int score = 0;

        String loc = location.toLowerCase();

        if (loc.contains("spor salonu"))                       score += 150;
        if (loc.contains("düğün salonu"))                      score += 150;
        if (loc.contains("kültür merkezi"))                    score += 150;
        if (loc.contains("gençlik merkezi"))                   score += 150;
        if (loc.contains("polis merkezi") || loc.contains("karakolu")) score += 150;
        if (loc.contains("hastanesi") || loc.contains("okulu"))score += 150;
        if (loc.contains("kongre merkezi"))                    score += 150;

        if (loc.contains("mahalle") || loc.contains("mah."))   score += 100;
        if (loc.contains("sokak")   || loc.contains("sok."))   score += 100;
        if (loc.contains("cadde")   || loc.contains("cad."))   score += 100;
        if (loc.contains("bulvar"))                            score += 80;
        if (loc.contains("tünel")   || loc.contains("tüneli")) score += 80;
        if (loc.contains("viyadük") || loc.contains("viyadüğü"))score += 80;
        if (loc.contains("gişe")    || loc.contains("gişeler")) score += 80;
        if (loc.contains("köprü")   || loc.contains("köprüsü")) score += 80;
        if (loc.contains("kavşak")  || loc.contains("kavşağı")) score += 80;
        if (loc.contains("meydan")  || loc.contains("meydanı")) score += 80;
        if (loc.contains("tesis")   || loc.contains("tesisi"))  score += 70;
        if (loc.contains("sanayi"))                            score += 70;
        if (loc.contains("mevki"))                             score += 60;
        if (loc.contains("yolu")    || loc.contains("yol"))    score += 50;
        if (loc.contains("köy"))                               score += 40;
        if (loc.contains("ilçe")    || loc.contains("merkez")) score += 20;

        int kelimeSayisi = location.split("\\s+").length;
        if (kelimeSayisi > 3) {
            score -= (kelimeSayisi - 3) * 10;
        }

        score += Math.min(location.length(), 30);
        return score;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledDeduplication() {
        log.info("Otomatik mükerrer haber temizliği (Toplu Temizlik) başlatılıyor...");
        deduplicateAndKeepMostSpecific();
    }

    public void deduplicateAndKeepMostSpecific() {
        try {
            List<Haber> allNews = haberRepository.findAll();
            Set<String> toDeleteIds = new HashSet<>();
            int removedCount = 0;

            log.info("Benzer haberler analiz ediliyor ({} haber)...", allNews.size());

            Map<String, List<Haber>> byCategory = allNews.stream()
                    .collect(Collectors.groupingBy(h -> h.getHaberTuru() == null ? "_NULL_" : h.getHaberTuru()));

            for (List<Haber> categoryNews : byCategory.values()) {
                if (categoryNews.size() < 2) {
                    continue;
                }

                UnionFind uf = new UnionFind(categoryNews.size());

                for (int i = 0; i < categoryNews.size(); i++) {
                    for (int j = i + 1; j < categoryNews.size(); j++) {
                        if (isSameEventPair(categoryNews.get(i), categoryNews.get(j))) {
                            uf.union(i, j);
                        }
                    }
                }

                Map<Integer, List<Haber>> components = new HashMap<>();
                for (int i = 0; i < categoryNews.size(); i++) {
                    int root = uf.find(i);
                    components.computeIfAbsent(root, k -> new ArrayList<>()).add(categoryNews.get(i));
                }

                for (List<Haber> component : components.values()) {
                    if (component.size() < 2) {
                        continue;
                    }

                    Haber keep = component.get(0);
                    boolean keepUpdated = false;

                    for (int i = 1; i < component.size(); i++) {
                        Haber candidate = component.get(i);
                        DedupDecision decision = pickKeepAndMerge(keep, candidate);

                        keep = decision.keep();
                        keepUpdated = keepUpdated || decision.updatedKeep() || keep == candidate;

                        if (decision.remove() != null && decision.remove().getId() != null) {
                            toDeleteIds.add(decision.remove().getId());
                        }

                        log.info("Silindi: '{}' | Tutuldu: '{}' | Sebep: {}",
                                decision.remove() != null ? decision.remove().getBaslik() : "-",
                                decision.keep() != null ? decision.keep().getBaslik() : "-",
                                decision.reason());
                    }

                    if (keepUpdated && keep != null) {
                        haberRepository.save(keep);
                    }
                }
            }

            for (Haber h : allNews) {
                if (toDeleteIds.contains(h.getId())) {
                    haberRepository.delete(h);
                    removedCount++;
                }
            }

            log.info(removedCount > 0 ? "{} mükerrer haber silindi." : " Silinecek haber bulunamadı.", removedCount);

        } catch (Exception e) {
            log.error("Deduplication hatası: {}", e.getMessage(), e);
        }
    }

    private boolean isSameEventPair(Haber haber1, Haber haber2) {
        if (haber1 == null || haber2 == null) {
            return false;
        }

        double titleLevenshtein = similarityService.calculateSimilarity(
                normalize(haber1.getBaslik()), normalize(haber2.getBaslik()));
        double titleCosine = similarityService.calculateCosineSimilarity(
                normalize(haber1.getBaslik()), normalize(haber2.getBaslik()));
        double contentCosine = similarityService.calculateCosineSimilarity(
                normalizeContent(haber1.getIcerik()), normalizeContent(haber2.getIcerik()));

        boolean isSameEvent;
        if ((titleCosine >= 0.40 || titleLevenshtein >= 0.50) && contentCosine >= 0.30) {
            isSameEvent = true;
        } else if (titleCosine >= 0.20 && contentCosine >= 0.70) {
            isSameEvent = true;
        } else {
            isSameEvent = contentCosine >= 0.85;
        }

        if (!isSameEvent || !hasSameDate(haber1, haber2)) {
            return false;
        }

        if (hasStrongLocationConflict(haber1, haber2)) {
            log.info("Mükerrer reddedildi (konum çakışması): '{}' <> '{}'",
                    haber1.getBaslik(), haber2.getBaslik());
            return false;
        }

        return true;
    }

    private boolean hasStrongLocationConflict(Haber h1, Haber h2) {
        LocationAnchors a1 = extractLocationAnchors(h1);
        LocationAnchors a2 = extractLocationAnchors(h2);

        if (!a1.roadCodes().isEmpty() && !a2.roadCodes().isEmpty()
                && !hasAnyAnchorOverlap(a1.roadCodes(), a2.roadCodes(), true)) {
            return true;
        }

        if (!a1.placeAnchors().isEmpty() && !a2.placeAnchors().isEmpty()
                && !hasAnyAnchorOverlap(a1.placeAnchors(), a2.placeAnchors(), false)) {
            return true;
        }

        return false;
    }

    private boolean hasAnyAnchorOverlap(Set<String> left, Set<String> right, boolean exactOnly) {
        for (String l : left) {
            for (String r : right) {
                if (Objects.equals(l, r)) {
                    return true;
                }

                if (!exactOnly && similarityService.calculateSimilarity(l, r) >= 0.80) {
                    return true;
                }
            }
        }
        return false;
    }

    private LocationAnchors extractLocationAnchors(Haber haber) {
        if (haber == null) {
            return new LocationAnchors(Collections.emptySet(), Collections.emptySet());
        }

        String source = normalize((haber.getBaslik() == null ? "" : haber.getBaslik())
                + " "
                + (haber.getIcerik() == null ? "" : haber.getIcerik())
                + " "
                + (haber.getKonumMetni() == null ? "" : haber.getKonumMetni()));

        Set<String> roadCodes = new HashSet<>();
        java.util.regex.Matcher roadMatcher = ROAD_CODE_PATTERN.matcher(source);
        while (roadMatcher.find()) {
            roadCodes.add(roadMatcher.group().replaceAll("[^a-z0-9]", ""));
        }

        Set<String> placeAnchors = new HashSet<>();
        java.util.regex.Matcher placeMatcher = PLACE_ANCHOR_PATTERN.matcher(source);
        while (placeMatcher.find()) {
            String phrase = (placeMatcher.group(1) + " " + placeMatcher.group(2)).trim();
            if (phrase.length() > 4) {
                placeAnchors.add(phrase);
            }
        }

        return new LocationAnchors(roadCodes, placeAnchors);
    }

    private boolean hasSameDate(Haber h1, Haber h2) {
        String content1 = ((h1.getBaslik() != null ? h1.getBaslik() : "") + " " + (h1.getIcerik() != null ? h1.getIcerik() : "")).toLowerCase();
        String content2 = ((h2.getBaslik() != null ? h2.getBaslik() : "") + " " + (h2.getIcerik() != null ? h2.getIcerik() : "")).toLowerCase();

        List<String> dates1 = extractDates(content1);
        List<String> dates2 = extractDates(content2);

        if (!dates1.isEmpty() && !dates2.isEmpty()) {
            for (String d1 : dates1) {
                if (dates2.contains(d1)) return true;
            }
            return false;
        }
        return true;
    }

    private List<String> extractDates(String text) {
        List<String> dates = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b\\d{1,2}\\s+(ocak|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık)\\b|\\b\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\b|\\b(pazartesi|salı|çarşamba|perşembe|cuma|cumartesi|pazar)\\b").matcher(text);
        while (m.find()) {
            dates.add(m.group());
        }
        return dates;
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

    private record LocationAnchors(Set<String> roadCodes, Set<String> placeAnchors) {}

    private static class UnionFind {
        private final int[] parent;
        private final int[] rank;

        private UnionFind(int n) {
            this.parent = new int[n];
            this.rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        private int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        private void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) {
                return;
            }

            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
        }
    }
}