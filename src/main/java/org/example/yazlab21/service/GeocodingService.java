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

        // 1. Önce Hangi İlçede Olduğunu Kesin Olarak Bul
        // İlçesi belirtilmeyen ama mahallesi belirtilenler yanlış ilçeye gidebilir.
        // O yüzden önce tam ilçeyi yakalıyoruz ki API'de ararken kesin o ilçeyi verelim.
        for (String district : KOCAELI_DISTRICTS) {
            String regex = "(?i)\\b" + district + "\\b"; // Kelime bütünlüğü için regex
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) {
                foundDistrict = district;
                break;
            }
        }

        // Eğer hiç ilçe bulamadıysa ama genel Kocaeli geçiyorsa İzmit (Merkez) varsayımı yapabiliriz veya boş bırakırız.


        // 2. Mahalle ve Cadde/Sokak bul
        java.util.List<String> detailsList = new java.util.ArrayList<>();
        Pattern locationPattern = Pattern.compile(
                "([A-Za-z0-9ÇĞİÖŞÜçğıöşü]+(?:\\s+[A-Za-z0-9ÇĞİÖŞÜçğıöşü]+){0,2}\\s+(Mahallesi|Mah\\.|Cadde|Caddesi|Sokak|Sokağı|Mevkii|Mevki|Köyü|Yolu))",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = locationPattern.matcher(text);

        while (matcher.find()) {
            String match = matcher.group(1).trim();
            if (!detailsList.contains(match)) {
                detailsList.add(match);
            }
        }

        if (detailsList.isEmpty()) {
            // Eğer regex bulamazsa bilinen mahalleleri kontrol et (İlçe bağımsız, riskli olabilir ama şimdilik kalsın)
            String[] bilinenMahalleler = {"Yahya Kaptan", "Yenişehir", "Bekirdere", "Karabaş", "Yenidoğan", "Plajyolu", "Sanayi", "Yuvam Akarca", "Ovacık", "Yuvacık", "İstasyon"};
            for (String mahalle : bilinenMahalleler) {
                if (text.toLowerCase().contains(mahalle.toLowerCase())) {
                    detailsList.add(mahalle + " Mahallesi");
                    break;
                }
            }
        }

        String foundDetail = String.join(", ", detailsList);

        // 3. Bulunanları birleştir - İLÇEYİ KESİNLİKLE SONA EKLE
        if (!foundDetail.isEmpty() && !foundDistrict.isEmpty()) {
            return foundDetail + ", " + foundDistrict; // Örn: İstasyon Mahallesi, Kartepe
        } else if (!foundDetail.isEmpty()) {
            // İlçe bulamadıysa ama mahalle bulduysa, İzmit varsayımını ekleyerek geocode şansını yükselt
            return foundDetail + ", İzmit";
        } else if (!foundDistrict.isEmpty()) {
            return foundDistrict;
        }

        // Hiçbir şey bulunamazsa merkez kabul et
        return "İzmit";
    }

    /**
     * Çıkarılan adresi OpenStreetMap (Nominatim) API'sine sorarak koordinatları alır
     */
    public LocationCoordinates getKocaeliLocationCoordinates(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return null;
        }

        try {
            // API'ye saygılı istek
            Thread.sleep(1000);

            // 1. Deneme: Tam Adres ile Ara. İL, İLÇE zorlaması ekleyerek yanlış ilçe/ile gitmesini önlüyoruz.
            String tamAdres = locationName;
            if (!tamAdres.toLowerCase().contains("kocaeli")) {
                tamAdres += ", Kocaeli";
            }

            LocationCoordinates result = callNominatimAPI(tamAdres, locationName);
            if (result != null) return result;

            // 2. Deneme: Virgülle ayrılmış bir adres ise (Örn "İstasyon Mahallesi, Kartepe")
            if (locationName.contains(",")) {
                String[] parts = locationName.split(",");
                String detay = parts[0].trim();
                String ilce = parts.length > 1 ? parts[1].trim() : "";

                Thread.sleep(1000);
                // "İstasyon Mahallesi, Kartepe, Kocaeli" şeklinde arat.
                String ilceZorlamaliAdres = detay + (ilce.isEmpty() ? "" : ", " + ilce) + ", Kocaeli";
                log.info("🔄 Tam adres bulunamadı, genişletilmiş arama deneniyor: {}", ilceZorlamaliAdres);

                result = callNominatimAPI(ilceZorlamaliAdres, locationName);
                if (result != null) return result;

                // 3. Deneme: Sadece İlçe (Detay yanlış girildiyse veya bulamıyorsa ilçeden devam et)
                if (!ilce.isEmpty()) {
                    Thread.sleep(1000);
                    String sadeceIlce = ilce + ", Kocaeli";
                    log.info("🔄 Detay bulunamadı, sadece ilçeden deneniyor: {}", sadeceIlce);
                    result = callNominatimAPI(sadeceIlce, locationName);
                    if (result != null) return result;
                }
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

        // En kötü ihtimal merkez İzmit'e düş
        return new LocationCoordinates("İzmit", 40.7671, 29.9427, "İzmit, Kocaeli");
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