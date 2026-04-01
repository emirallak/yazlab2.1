package org.example.yazlab21.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String REGION_CONTEXT = "Kocaeli";

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
    // Örnek: "İzmit'te", "Körfez Caddesi'nde", "D-100'de"
    private static final Pattern KESME_BULUNMA = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ0-9\\s\\-]{1,60}?)" +
                    "(?:'de|'da|'te|'ta|'nde|'nda|'de|'da|'te|'ta|'nde|'nda)",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Kural 2: Yer tipi anahtar kelimeleri
    // Örnek: "Körfez Caddesi", "Yenişehir Mahallesi"
    private static final Pattern YER_TIPI = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ0-9\\s\\-]{1,50}?)" +
                    "\\s*(Mahallesi|Mah\\.|Caddesi|Cad\\.|Bulvarı|Blv\\.|Sokağı|Sokak|Sk\\." +
                    "|Yolu|Kavşağı|Meydanı|Köyü|Sitesi|Sanayi Sitesi|Sanayi|Limanı" +
                    "|Köprüsü|Tüneli|Stadyumu|Hastanesi|Okulu|Camii|Parkı|Ormanı)",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Kural 3: "X ilçesinde / semtinde" kalıpları
    // Örnek: "Gebze ilçesinde", "Körfez semtinde"
    private static final Pattern ILCE_KALIBI = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]{2,30})" +
                    "\\s+(?:ilçesinde|semtinde|bölgesinde|mahallesinde|köyünde)",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // ────────────────────────────────────────────────────────────────────────

    /**
     * Ana metot: Haber metnindeki TÜM konum adaylarını çıkarır,
     * geocoding uygular ve koordinat bulunanları döndürür.
     * Liste boşsa haber haritada gösterilmez.
     */
    public List<LocationCoordinates> extractAndGeocodeAll(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        List<String> adaylar = extractAllLocationCandidates(text);
        log.info("🔍 Toplam {} konum adayı tespit edildi: {}", adaylar.size(), adaylar);

        List<LocationCoordinates> sonuclar = new ArrayList<>();
        Set<String> sorgulanmis = new HashSet<>();

        for (String aday : adaylar) {
            if (sorgulanmis.contains(aday.toLowerCase())) continue;
            sorgulanmis.add(aday.toLowerCase());

            LocationCoordinates koordinat = callNominatimAPI(aday.trim() + ", " + REGION_CONTEXT, aday);
            if (koordinat != null && koordinat.isValid()) {
                sonuclar.add(koordinat);
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

        // Kural 1: Kesme işaretli bulunma hali
        Matcher m1 = KESME_BULUNMA.matcher(text);
        while (m1.find()) {
            String aday = temizle(m1.group(1));
            if (gecerliMi(aday)) adaySet.add(aday);
        }

        // Kural 2: Yer tipi anahtar kelimeleri — tam ifade önce, sonra prefix
        Matcher m2 = YER_TIPI.matcher(text);
        while (m2.find()) {
            String tamIfade = temizle(m2.group(0));
            String sadecePrefiks = temizle(m2.group(1));
            if (gecerliMi(tamIfade)) adaySet.add(tamIfade);
            if (gecerliMi(sadecePrefiks)) adaySet.add(sadecePrefiks);
        }

        // Kural 3: "X ilçesinde" kalıpları
        Matcher m3 = ILCE_KALIBI.matcher(text);
        while (m3.find()) {
            String aday = temizle(m3.group(1));
            if (gecerliMi(aday)) adaySet.add(aday);
        }

        // Kural 4: Bilinen ilçe adlarını doğrudan ara
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
        return callNominatimAPI(locationName.trim() + ", " + REGION_CONTEXT, locationName);
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

    // ─── Nominatim API ──────────────────────────────────────────────────────

    // Uygulama genelinde son istek zamanını tut
    private static volatile long sonIstekZamani = 0L;
    private static final long MIN_ISTEK_ARALIGI_MS = 2000; // 2 saniye — güvenli taraf
    private static final int MAX_RETRY = 3;

    private LocationCoordinates callNominatimAPI(String searchQuery, String originalName) {
        for (int deneme = 1; deneme <= MAX_RETRY; deneme++) {
            try {
                // Global throttle: son istekten bu yana yeterli süre geçmediyse bekle
                synchronized (GeocodingService.class) {
                    long simdi = System.currentTimeMillis();
                    long gecen = simdi - sonIstekZamani;
                    if (gecen < MIN_ISTEK_ARALIGI_MS) {
                        Thread.sleep(MIN_ISTEK_ARALIGI_MS - gecen);
                    }
                    sonIstekZamani = System.currentTimeMillis();
                }

                String url = UriComponentsBuilder.fromUriString(NOMINATIM_URL)
                        .queryParam("q", searchQuery)
                        .queryParam("format", "json")
                        .queryParam("limit", 1)
                        .queryParam("countrycodes", "tr")
                        .queryParam("addressdetails", 1)
                        .build()
                        .toUriString();

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", "YazlabHaberHaritasi/1.0 (university project)");
                headers.set("Accept-Language", "tr,en");
                org.springframework.http.HttpEntity<String> entity =
                        new org.springframework.http.HttpEntity<>(headers);

                @SuppressWarnings("unchecked")
                org.springframework.http.ResponseEntity<List> response = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, entity, List.class
                );

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = response.getBody();

                if (results != null && !results.isEmpty()) {
                    Map<String, Object> first = results.get(0);
                    double lat = Double.parseDouble(first.get("lat").toString());
                    double lon = Double.parseDouble(first.get("lon").toString());
                    String displayName = first.get("display_name").toString();
                    String osmClass = first.getOrDefault("class", "unknown").toString();
                    String osmType  = first.getOrDefault("type",  "unknown").toString();

                    log.info("✅ Koordinat bulundu → '{}' : ({}, {}) [{}]",
                            searchQuery, lat, lon, osmType);
                    return new LocationCoordinates(originalName, lat, lon, displayName, osmClass, osmType);
                } else {
                    log.warn("⚠️  Sonuç bulunamadı → '{}'", searchQuery);
                    return null; // Retry'a gerek yok, API cevap verdi ama sonuç yok
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long bekleme = (long) Math.pow(2, deneme) * 3000L; // 6s, 12s, 24s
                    log.warn("⏳ 429 Rate limit (deneme {}/{}), {}ms bekleniyor → '{}'",
                            deneme, MAX_RETRY, bekleme, searchQuery);
                    try {
                        Thread.sleep(bekleme);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    // MAX_RETRY'a ulaştıysak null dön
                    if (deneme == MAX_RETRY) {
                        log.error("❌ {} denemede de 429 alındı, atlanıyor: '{}'", MAX_RETRY, searchQuery);
                        return null;
                    }
                } else {
                    log.error("❌ HTTP hatası ('{}') : {}", searchQuery, e.getMessage());
                    return null;
                }
            } catch (Exception e) {
                log.error("❌ Nominatim API hatası ('{}') : {}", searchQuery, e.getMessage());
                return null;
            }
        }
        return null;
    }

    // ─── Yardımcı Metotlar ──────────────────────────────────────────────────

    private String temizle(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s{2,}", " ");
    }

    private boolean gecerliMi(String s) {
        if (s == null || s.length() < 3) return false;

        // Çok uzun stringler gürültüdür (ör. "Gece saatlerinde Hacıhızır Mahallesi Bağlar Yolu")
        if (s.length() > 50) return false;

        // Bilinen gürültü kelimeleri
        if (GURULTU_KELIMELERI.contains(s)) return false;

        // Zaman zarflarıyla başlayanlar konum değildir
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
            this.locationName    = locationName;
            this.latitude        = latitude;
            this.longitude       = longitude;
            this.formattedAddress = formattedAddress;
            this.osmClass        = osmClass;
            this.osmType         = osmType;
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