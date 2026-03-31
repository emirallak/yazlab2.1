package org.example.yazlab21.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.List;

@Slf4j
@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    // OpenStreetMap Nominatim API URL
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";

    // Kocaeli'deki ana ilçeler
    private static final String[] KOCAELI_DISTRICTS = {
            "İzmit", "Körfez", "Derince", "Gölcük", "Başiskele", "Kandıra",
            "Çayırova", "Dilovası", "Kartepe", "Gebze", "Darıca", "Karamürsel"
    };

    /**
     * Metinden konum bilgisini çıkarır
     */
    public String extractLocationFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String foundDistrict = "";

        // 1. Önce Hangi İlçede Olduğunu Bul
        for (String district : KOCAELI_DISTRICTS) {
            // Sadece kelime olarak geçiyorsa al (örn: İzmit)
            if (text.toLowerCase().matches(".*\\b" + district.toLowerCase() + "\\b.*")) {
                foundDistrict = district;
                break;
            }
        }

        String mahalle = "";
        String cadde = "";
        String sokak = "";
        String karayolu = "";

        // Özel bilinen büyük mahalleleri manuel yakala
        String[] bilinenMahalleler = {"Yahya Kaptan", "Yenişehir", "Bekirdere", "Karabaş", "Yenidoğan", "Plajyolu", "Sanayi", "Yuvam Akarca"};
        for (String m : bilinenMahalleler) {
            if (text.toLowerCase().contains(m.toLowerCase())) {
                mahalle = m + " Mahallesi";
                break;
            }
        }

        // Karayolu, D100 vb. bul
        Matcher mYol = Pattern.compile("(?i)\\b(D-?100|E-?80|D-?130|Kuzey Marmara Otoyolu|Anadolu Otoyolu|TEM Otoyolu|D100)\\b").matcher(text);
        if (mYol.find()) {
            String yol = mYol.group(1).toUpperCase().replace("D100", "D-100");
            karayolu = yol + " Karayolu";
        }

        String bulvar = "";
        // Bulvar, Yol, Kavşak, Meydan, Site (Örn: "Kandıra Yolu", "Turan Güneş Bulvarı", "Sanayi Sitesi")
        // Kelimelerin en azından ilki büyük harfle başlamalı, ancak son kelime (Yolu vb.) küçük yazılmış olabilir.
        Matcher mBulvar = Pattern.compile("([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]*\\s+){1,3}(?i)(Bulvarı|Yolu|Kavşağı|Mevkii|Sitesi|Meydanı)").matcher(text);
        if (mBulvar.find()) {
            bulvar = mBulvar.group(0).trim();
        }

        // Mahalle bul (Sadece Büyük harfle başlayan kelimeleri almasını sağlamak için)
        // Örn: "Cumhuriyet Mahallesi", "Hacı Hasan Mah."
        if (mahalle.isEmpty()) {
            Matcher mMahalle = Pattern.compile("([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]*\\s+){1,3}(?i)(Mahallesi|Mah\\.)").matcher(text);
            if (mMahalle.find()) mahalle = mMahalle.group(0).replaceAll("(?i)Mah\\.", "Mahallesi").trim();
        }

        // Cadde bul
        Matcher mCadde = Pattern.compile("([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]*\\s+){1,3}(?i)(Caddesi|Cadde|Cad\\.)").matcher(text);
        if (mCadde.find()) cadde = mCadde.group(0).replaceAll("(?i)Cad\\.", "Caddesi").trim();

        // Sokak bul
        Matcher mSokak = Pattern.compile("([A-ZÇĞİÖŞÜ0-9][a-zçğıöşüA-ZÇĞİÖŞÜ0-9]*\\s+){1,3}(?i)(Sokağı|Sokak|Sok\\.)").matcher(text);
        if (mSokak.find()) sokak = mSokak.group(0).replaceAll("(?i)Sok\\.", "Sokak").trim();

        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!karayolu.isEmpty()) parts.add(karayolu);
        if (!sokak.isEmpty()) parts.add(sokak);
        if (!cadde.isEmpty()) parts.add(cadde);
        if (!bulvar.isEmpty()) parts.add(bulvar);
        if (!mahalle.isEmpty()) parts.add(mahalle);
        if (!foundDistrict.isEmpty()) parts.add(foundDistrict);

        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }

        return null;
    }

    /**
     * Çıkarılan adresi OpenStreetMap (Nominatim) API'sine sorarak koordinatları alır
     */
    public LocationCoordinates getKocaeliLocationCoordinates(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return null;
        }

        try {
            // "Sokak, Cadde, Mahalle, İlçe" gibi virgülle ayrılmış bir liste bekliyoruz
            java.util.List<String> list = new java.util.ArrayList<>(java.util.Arrays.asList(locationName.split(",\\s*")));

            // Kocaeli yoksa sonuna ekle
            if (!list.contains("Kocaeli")) {
                list.add("Kocaeli");
            }

            LocationCoordinates result = null;

            // 1. AŞAMA: Tam adresi sırayla azaltarak ara
            java.util.List<String> currentSearchList = new java.util.ArrayList<>(list);
            while (currentSearchList.size() > 1) { // Sadece Kocaeli kalana kadar
                String searchQuery = String.join(", ", currentSearchList);

                Thread.sleep(1000);
                log.info("📍 Nominatim Aranıyor (Normal): {}", searchQuery);
                result = callNominatimAPI(searchQuery, locationName);
                if (result != null) return result;

                // Bulamadıysa en baştakini (en spesifik olanı) çıkar
                currentSearchList.remove(0);
            }

            // 2. AŞAMA: Eğer hiçbirini bulamadıysa, "Mahallesi", "Caddesi", "Sokak" kelimelerini TEMİZLEYİP ara (Nominatim bazen kelime eklendiğinde bulamıyor)
            currentSearchList = new java.util.ArrayList<>(list);
            while (currentSearchList.size() > 1) {
                // Her elemanın içindeki ek kelimeleri çıkar (Örn: "Cumhuriyet Mahallesi" -> "Cumhuriyet")
                java.util.List<String> cleanedList = new java.util.ArrayList<>();
                for (String part : currentSearchList) {
                    if (part.equals("Kocaeli") || KOCAELI_DISTRICTS_CONTAINS(part)) {
                        cleanedList.add(part);
                    } else {
                        String clean = part.replaceAll("(?i)\\s+(Mahallesi|Cadde|Caddesi|Sokak|Sokağı|Karayolu|Bulvarı|Yolu|Kavşağı|Mevkii|Sitesi|Meydanı)", "").trim();
                        cleanedList.add(clean);
                    }
                }

                String searchQueryCleaned = String.join(", ", cleanedList);
                Thread.sleep(1000);
                log.info("📍 Nominatim Aranıyor (Temizlenmiş): {}", searchQueryCleaned);
                result = callNominatimAPI(searchQueryCleaned, locationName);
                if (result != null) return result;

                currentSearchList.remove(0);
            }

            // Hiçbiri bulamazsa yedek sisteme düş
            log.warn("❌ Tüm API aramaları '{}' için sonuçsuz kaldı. Yedek sisteme (İlçe) geçiliyor.", locationName);
            return getFallbackCoordinates(locationName);

        } catch (Exception e) {
            log.error("Geocoding API hatası: {}", e.getMessage());
            return getFallbackCoordinates(locationName);
        }
    }

    /**
     * İlçe listesinde olup olmadığını kontrol eden yardımcı metod
     */
    private boolean KOCAELI_DISTRICTS_CONTAINS(String district) {
        for (String d : KOCAELI_DISTRICTS) {
            if (d.equalsIgnoreCase(district)) return true;
        }
        return false;
    }

    /**
     * API'ye istek atan yardımcı metod
     */
    private LocationCoordinates callNominatimAPI(String searchQuery, String originalName) {
        try {
            String url = UriComponentsBuilder.fromUriString(NOMINATIM_URL)
                    .queryParam("q", searchQuery)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    // Türkiye içi sonuçlara öncelik vermek için ülke kodu
                    .queryParam("countrycodes", "tr")
                    .build()
                    .toUriString();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "YazlabHaberHaritasi/1.0");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    List.class
            );

            List<Map<String, Object>> results = response.getBody();

            if (results != null && !results.isEmpty()) {
                Map<String, Object> firstResult = results.get(0);
                double lat = Double.parseDouble(firstResult.get("lat").toString());
                double lon = Double.parseDouble(firstResult.get("lon").toString());
                String displayName = firstResult.get("display_name").toString();

                log.info("📍 Harita API Koordinat Buldu: {} -> ({}, {})", searchQuery, lat, lon);
                // Dönen ismi orijinal isim olarak tutuyoruz ki arayüzde saçma sapan uzun adresler çıkmasın
                return new LocationCoordinates(originalName, lat, lon, displayName);
            }
        } catch (Exception e) {
            log.warn("API isteği başarısız oldu ({}): {}", searchQuery, e.getMessage());
        }
        return null;
    }

    /**
     * Eğer API cevap vermezse veya adresi bulamazsa eski sistemdeki gibi ilçe merkezini döndürür.
     * Denizin ortasına düşen koordinatlar düzeltilmiştir.
     */
    private LocationCoordinates getFallbackCoordinates(String locationName) {
        String lowerName = locationName.toLowerCase();

        if (lowerName.contains("izmit")) return new LocationCoordinates("İzmit", 40.7671, 29.9427, "İzmit, Kocaeli");
        if (lowerName.contains("körfez")) return new LocationCoordinates("Körfez", 40.7900, 29.7400, "Körfez, Kocaeli"); // Denizden karaya çekildi
        if (lowerName.contains("derince")) return new LocationCoordinates("Derince", 40.7550, 29.8300, "Derince, Kocaeli");
        // Gölcük koordinatı karaya alındı (Önceki deniz ortasıydı)
        if (lowerName.contains("gölcük")) return new LocationCoordinates("Gölcük", 40.7180, 29.8200, "Gölcük, Kocaeli");
        if (lowerName.contains("başiskele")) return new LocationCoordinates("Başiskele", 40.7167, 29.9333, "Başiskele, Kocaeli");
        if (lowerName.contains("kandıra")) return new LocationCoordinates("Kandıra", 41.0667, 30.1500, "Kandıra, Kocaeli");
        if (lowerName.contains("çayırova")) return new LocationCoordinates("Çayırova", 40.8250, 29.3800, "Çayırova, Kocaeli");
        if (lowerName.contains("dilovası")) return new LocationCoordinates("Dilovası", 40.7850, 29.5400, "Dilovası, Kocaeli");
        if (lowerName.contains("kartepe")) return new LocationCoordinates("Kartepe", 40.7500, 30.0167, "Kartepe, Kocaeli");
        if (lowerName.contains("gebze")) return new LocationCoordinates("Gebze", 40.8000, 29.4300, "Gebze, Kocaeli");
        if (lowerName.contains("darıca")) return new LocationCoordinates("Darıca", 40.7667, 29.4000, "Darıca, Kocaeli");
        if (lowerName.contains("karamürsel")) return new LocationCoordinates("Karamürsel", 40.6917, 29.6167, "Karamürsel, Kocaeli");

        return null;
    }

    /**
     * İç sınıf: Konum Koordinatları
     */
    public static class LocationCoordinates {
        public String locationName;
        public double latitude;
        public double longitude;
        public String formattedAddress;

        public LocationCoordinates(String locationName, double latitude, double longitude, String formattedAddress) {
            this.locationName = locationName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.formattedAddress = formattedAddress;
        }

        public boolean isValid() {
            return latitude != 0 && longitude != 0;
        }
    }
}
