package org.example.yazlab21.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    // Gereksiz API çağrılarını engellemek için önbellek mekanizması
    private final Map<String, LocationCoordinates> geocodeCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> negativeCache = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final Map<String, LocationCoordinates> ILCE_MERKEZLERI = new HashMap<>();
    static {
        ILCE_MERKEZLERI.put("izmit", new LocationCoordinates("İzmit", 40.7661, 29.9236, "İzmit, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("gebze", new LocationCoordinates("Gebze", 40.8028, 29.4307, "Gebze, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("darıca", new LocationCoordinates("Darıca", 40.7732, 29.4093, "Darıca, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("körfez", new LocationCoordinates("Körfez", 40.7794, 29.7375, "Körfez, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("başiskele", new LocationCoordinates("Başiskele", 40.7144, 29.9125, "Başiskele, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("kartepe", new LocationCoordinates("Kartepe", 40.7490, 30.0205, "Kartepe, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("çayırova", new LocationCoordinates("Çayırova", 40.8258, 29.3878, "Çayırova, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("dilovası", new LocationCoordinates("Dilovası", 40.7818, 29.5407, "Dilovası, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("gölcük", new LocationCoordinates("Gölcük", 40.7180, 29.8210, "Gölcük, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("kandıra", new LocationCoordinates("Kandıra", 41.0706, 30.1506, "Kandıra, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
        ILCE_MERKEZLERI.put("derince", new LocationCoordinates("Derince", 40.7570, 29.8320, "Derince, Kocaeli, Türkiye", "place", "administrative_area_level_2"));
    }

    @Value("${google.geocoding.api-key}")
    private String apiKey;

    private static final String GOOGLE_GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String REGION_CONTEXT = "Kocaeli, Türkiye";

    private static final List<String> BILINEN_ILCELER = Arrays.asList(
            "İzmit", "Gebze", "Darıca", "Körfez", "Başiskele", "Kartepe",
            "Çayırova", "Dilovası", "Gölcük", "Kandıra", "Karamürsel",
            "Derince", "Karabaş", "Arslanbey", "Hereke", "Tavşantepe"
    );

    private static final Set<String> GURULTU_KELIMELERI = new HashSet<>(Arrays.asList(
            "bir", "bu", "şu", "o", "ve", "ile", "da", "de", "ki",
            "için", "gibi", "kadar", "sonra", "önce", "ancak", "Ancak",
            "Saat", "Gece", "Sabah", "Akşam", "Öğle", "Gündüz",
            "Türkiye", "İstanbul"
    ));

    private static final String[] ZAMAN_ZARFLARI = {
            "Gece", "Sabah", "Akşam", "Saat", "Öğle", "Gündüz",
            "Dün", "Bugün", "Yarın", "Hafta"
    };

    // Kural 1: Kesme işaretli bulunma hali
    private static final Pattern KESME_BULUNMA = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ0-9\\s\\-]{1,60}?)" +
                    "(?:'de|'da|'te|'ta|'nde|'nda|'de|'da|'te|'ta|'nde|'nda)",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Kural 2: Yer tipi anahtar kelimeleri
    private static final Pattern YER_TIPI = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ0-9\\s\\-]{1,50}?)" +
                    "\\s*(Mahallesi|Mah\\.|Caddesi|Cad\\.|Bulvarı|Blv\\.|Sokağı|Sokak|Sk\\." +
                    "|Yolu|Kavşağı|Meydanı|Köyü|Sitesi|Sanayi Sitesi|Sanayi|Limanı" +
                    "|Köprüsü|Tüneli|Stadyumu|Hastanesi|Okulu|Camii|Parkı|Ormanı" +
                    "|Spor Salonu|Düğün Salonu|Kültür Merkezi|Gençlik Merkezi|Sosyal Tesisleri|Karakolu|Polis Merkezi|Kongre Merkezi" +
                    "|Gişeleri|Gişesi|Gişeler|Viyadüğü|Viyadük|Gişelerinde)",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Kural 3: "X ilçesinde / semtinde" kalıpları
    private static final Pattern ILCE_KALIBI = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]{2,30})" +
                    "\\s+(?:ilçesinde|semtinde|bölgesinde|mahallesinde|köyünde)",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // ────────────────────────────────────────────────────────────────────────

    /**
     * Ana metot: Haber metnindeki TÜM konum adaylarını çıkarır,
     * geocoding uygular ve koordinat bulunanları döndürür.
     */
    public List<LocationCoordinates> extractAndGeocodeAll(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        List<String> adaylar = extractAllLocationCandidates(text);

        // Daha belirgin ve spesifik yer isimlerinin (Mahalle, Sokak vb.) önce aranması için uzunluğa göre sıralayalım
        adaylar.sort((a, b) -> Integer.compare(b.length(), a.length()));

        log.info("🔍 Toplam {} konum adayı tespit edildi: {}", adaylar.size(), adaylar);

        List<String> districtsInText = new ArrayList<>();
        for (String ilce : BILINEN_ILCELER) {
            if (Pattern.compile("\\b" + ilce + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS).matcher(text).find()) {
                districtsInText.add(ilce);
            }
        }

        List<LocationCoordinates> sonuclar = new ArrayList<>();
        Set<String> sorgulanmis = new HashSet<>();
        int validCount = 0;

        for (String aday : adaylar) {
            if (sorgulanmis.contains(aday.toLowerCase())) continue;
            sorgulanmis.add(aday.toLowerCase());

            LocationCoordinates koordinat = null;

            // Eğer aday bir ilçe değilse ve metinde ilçe geçiyorsa, önce ilçeyle birlikte aramayı dene
            if (!districtsInText.isEmpty() && !BILINEN_ILCELER.contains(aday)) {
                for (String ilce : districtsInText) {
                    koordinat = callGoogleGeocodingAPI(aday.trim() + " " + ilce + ", " + REGION_CONTEXT, aday);
                    if (koordinat != null && koordinat.isValid()) {
                        break;
                    }
                }
            }

            // Yukarıdaki deneme başarısız olduysa veya aday zaten bir ilçe ise normal arama yap
            if (koordinat == null || !koordinat.isValid()) {
                koordinat = callGoogleGeocodingAPI(aday.trim() + ", " + REGION_CONTEXT, aday);
            }

            if (koordinat != null && koordinat.isValid()) {
                sonuclar.add(koordinat);
                validCount++;
                // Gereksiz API kotalarını doldurmamak adına maksimum 3 geçerli koordinat bulduysak diğer daha genel adayları geocode etmeyi bırak
                if (validCount >= 3) break;
            }
        }

        log.info("✅ {} konum başarıyla koordinatlandı.", sonuclar.size());
        return sonuclar;
    }

    /**
     * Metinden tüm konum adaylarını çıkarır (geocoding yapmadan).
     */
    List<String> extractAllLocationCandidates(String text) {
        Set<String> adaySet = new LinkedHashSet<>();

        Matcher m1 = KESME_BULUNMA.matcher(text);
        while (m1.find()) {
            String aday = temizle(m1.group(1));
            if (gecerliMi(aday)) adaySet.add(aday);
        }

        Matcher m2 = YER_TIPI.matcher(text);
        while (m2.find()) {
            String tamIfade = temizle(m2.group(0));
            String sadecePrefiks = temizle(m2.group(1));
            if (gecerliMi(tamIfade)) adaySet.add(tamIfade);
            if (gecerliMi(sadecePrefiks)) adaySet.add(sadecePrefiks);
        }

        Matcher m3 = ILCE_KALIBI.matcher(text);
        while (m3.find()) {
            String aday = temizle(m3.group(1));
            if (gecerliMi(aday)) adaySet.add(aday);
        }

        for (String ilce : BILINEN_ILCELER) {
            if (text.contains(ilce)) {
                adaySet.add(ilce);
            }
        }

        return new ArrayList<>(adaySet);
    }

    /**
     * Tek bir konum adı için koordinat döndürür.
     */
    public LocationCoordinates getCoordinates(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) return null;
        return callGoogleGeocodingAPI(locationName.trim() + ", " + REGION_CONTEXT, locationName);
    }

    /** @deprecated extractAndGeocodeAll() kullanın. */
    @Deprecated
    public LocationCoordinates getKocaeliLocationCoordinates(String locationName) {
        return getCoordinates(locationName);
    }

    /** @deprecated extractAndGeocodeAll() kullanın. */
    @Deprecated
    public String extractLocationFromText(String text) {
        List<String> adaylar = extractAllLocationCandidates(text);
        return adaylar.isEmpty() ? null : adaylar.get(0);
    }

    // ─── Google Geocoding API ────────────────────────────────────────────────

    private static final int MAX_RETRY = 3;

    @SuppressWarnings("unchecked")
    private LocationCoordinates callGoogleGeocodingAPI(String searchQuery, String originalName) {
        if (originalName != null && ILCE_MERKEZLERI.containsKey(originalName.toLowerCase().trim())) {
            return ILCE_MERKEZLERI.get(originalName.toLowerCase().trim());
        }

        if (geocodeCache.containsKey(searchQuery)) {
            return geocodeCache.get(searchQuery);
        }
        if (negativeCache.contains(searchQuery)) {
            return null;
        }

        for (int deneme = 1; deneme <= MAX_RETRY; deneme++) {
            try {
                String url = UriComponentsBuilder.fromUriString(GOOGLE_GEOCODING_URL)
                        .queryParam("address", searchQuery)
                        .queryParam("key", apiKey)
                        .queryParam("language", "tr")
                        .queryParam("region", "tr")
                        .build()
                        .toUriString();

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("Accept-Language", "tr,en");
                org.springframework.http.HttpEntity<String> entity =
                        new org.springframework.http.HttpEntity<>(headers);

                org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        org.springframework.http.HttpMethod.GET,
                        entity,
                        Map.class
                );

                Map<String, Object> body = response.getBody();
                if (body == null) {
                    log.warn("⚠️  Boş yanıt → '{}'", searchQuery);
                    return null;
                }

                String status = (String) body.get("status");

                // Başarı durumu
                if ("OK".equals(status)) {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
                    if (results == null || results.isEmpty()) {
                        log.warn("⚠️  Sonuç listesi boş → '{}'", searchQuery);
                        return null;
                    }

                    Map<String, Object> firstResult = results.get(0);
                    Map<String, Object> geometry    = (Map<String, Object>) firstResult.get("geometry");
                    Map<String, Object> location    = (Map<String, Object>) geometry.get("location");

                    double lat = ((Number) location.get("lat")).doubleValue();
                    double lon = ((Number) location.get("lng")).doubleValue();
                    String formattedAddress = (String) firstResult.get("formatted_address");

                    // Yer türünü belirle (ilk types elemanı)
                    List<String> types = (List<String>) firstResult.get("types");
                    String placeType = (types != null && !types.isEmpty()) ? types.get(0) : "unknown";

                    // Kocaeli sınırları içinde mi? (basit bounding-box kontrolü)
                    if (!isWithinKocaeliBounds(lat, lon)) {
                        log.warn("⚠️  Kocaeli dışında koordinat, atlanıyor → '{}' ({}, {})",
                                searchQuery, lat, lon);
                        negativeCache.add(searchQuery);
                        return null;
                    }

                    log.info("✅ Koordinat bulundu → '{}' : ({}, {}) [{}]",
                            searchQuery, lat, lon, placeType);
                    LocationCoordinates result = new LocationCoordinates(originalName, lat, lon, formattedAddress, "place", placeType);
                    geocodeCache.put(searchQuery, result);
                    return result;
                }

                // Sonuç yok ama geçici hata değil
                if ("ZERO_RESULTS".equals(status)) {
                    log.warn("⚠️  Sonuç bulunamadı (ZERO_RESULTS) → '{}'", searchQuery);
                    negativeCache.add(searchQuery);
                    return null;
                }

                // Geçici hatalar — retry
                if ("OVER_QUERY_LIMIT".equals(status) || "UNKNOWN_ERROR".equals(status)) {
                    long bekleme = (long) Math.pow(2, deneme) * 2000L;
                    log.warn("⏳ {} hatası (deneme {}/{}), {}ms bekleniyor → '{}'",
                            status, deneme, MAX_RETRY, bekleme, searchQuery);
                    Thread.sleep(bekleme);
                    continue;
                }

                // REQUEST_DENIED, INVALID_REQUEST vb. — kalıcı hata, retry anlamsız
                log.error("❌ Google API hatası [{}] → '{}'", status, searchQuery);
                return null;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("❌ Google Geocoding API hatası ('{}') : {}", searchQuery, e.getMessage());
                if (deneme == MAX_RETRY) return null;
            }
        }
        return null;
    }

    /**
     * Kocaeli ili yaklaşık bounding-box kontrolü.
     * Google API bazen alakasız sonuç döndürebilir; bu kontrol bunu engeller.
     *
     *   Kuzey: 41.25  Güney: 40.40
     *   Batı : 29.25  Doğu : 30.85
     */
    private boolean isWithinKocaeliBounds(double lat, double lon) {
        return lat >= 40.40 && lat <= 41.25 && lon >= 29.25 && lon <= 30.85;
    }

    // ─── Yardımcı Metotlar ───────────────────────────────────────────────────

    private String temizle(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s{2,}", " ");
    }

    private boolean gecerliMi(String s) {
        if (s == null || s.length() < 3) return false;
        if (s.length() > 50) return false;
        if (GURULTU_KELIMELERI.contains(s)) return false;
        for (String zarf : ZAMAN_ZARFLARI) {
            if (s.startsWith(zarf)) return false;
        }
        return true;
    }

    // ─── Veri Sınıfı ────────────────────────────────────────────────────────

    public static class LocationCoordinates {

        public final String locationName;
        public final double latitude;
        public final double longitude;
        public final String formattedAddress;
        public final String osmClass;
        public final String osmType;

        public LocationCoordinates(String locationName, double latitude, double longitude,
                                   String formattedAddress, String osmClass, String osmType) {
            this.locationName     = locationName;
            this.latitude         = latitude;
            this.longitude        = longitude;
            this.formattedAddress = formattedAddress;
            this.osmClass         = osmClass;
            this.osmType          = osmType;
        }

        /** Geriye dönük uyumluluk için 4-parametre constructor. */
        public LocationCoordinates(String locationName, double latitude, double longitude,
                                   String formattedAddress) {
            this(locationName, latitude, longitude, formattedAddress, "", "");
        }

        /** (0,0) Atlas Okyanusu'nda olduğu için geçersizdir. */
        public boolean isValid() {
            return latitude != 0.0 && longitude != 0.0;
        }

        @Override
        public String toString() {
            return String.format("LocationCoordinates{name='%s', lat=%.6f, lon=%.6f, type='%s/%s'}",
                    locationName, latitude, longitude, osmClass, osmType);
        }
    }
}

