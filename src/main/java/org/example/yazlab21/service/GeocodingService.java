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
        String mahalle = "";
        String cadde = "";
        String sokak = "";
        String karayolu = "";
        String poi = "";
        String bulvar = "";

        String textLower = text.toLowerCase();

        // Özel bilinen büyük mahalleler ve semtleri ilçe garantili manuel yakala
        if (textLower.contains("yahya kaptan")) {
            mahalle = "Yahya Kaptan Mahallesi";
            foundDistrict = "İzmit";
        } else if (textLower.contains("yuvam akarca")) {
            mahalle = "Yuvam Akarca";
            foundDistrict = "İzmit";
        } else if (textLower.contains("bekirdere")) {
            mahalle = "Bekirdere Mahallesi";
            foundDistrict = "İzmit";
        } else if (textLower.contains("karabaş")) {
            mahalle = "Karabaş Mahallesi";
            foundDistrict = "İzmit";
        } else if (textLower.contains("plajyolu")) {
            mahalle = "Plajyolu";
            foundDistrict = "İzmit";
        }

        // 1. Önce Hangi İlçede Olduğunu Yakala (Eğer manuel bulunmadıysa)
        if (foundDistrict.isEmpty()) {
            for (String district : KOCAELI_DISTRICTS) {
                if (textLower.matches(".*\\b" + district.toLowerCase() + "\\b.*")) {
                    foundDistrict = district;
                    break;
                }
            }
        }

        // Tramvay durakları arası kaza veya direkt durak aranması özel durumu
        if (textLower.contains("tramvay") || textLower.contains("akçaray")) {
            // En uzun isimden en kısaya doğru sıralı ki "Yenişehir" vb. yanlış içerik eşleşmesi yapmasın
            String[] duraklar = {
                "Milli İrade Meydanı", "Eğitim Kampüsü", "Kongre Merkezi", "Mehmet Ali Paşa",
                "Yahya Kaptan", "Yeni Cuma", "Doğu Kışla", "Yenişehir", "Plajyolu",
                "Sekapark", "Seka Park", "Santral", "Fevziye", "Otogar", "Gar"
            };
            for (String d : duraklar) {
                if (textLower.contains(d.toLowerCase())) {
                    // API tarafinda "Tramvay İstasyonu" ibaresi daha kolay bulunuyor veya temizleniyor
                    poi = d.replace("Seka Park", "Sekapark") + " Tramvay İstasyonu";
                    foundDistrict = "İzmit";
                    break;
                }
            }
        }

        // Otogar kuralı - Eğer içinde tramvay geçmiyorsa direkt İzmit Otogarını kasteder
        if (poi.isEmpty() && textLower.matches(".*\\botogar\\b.*") && !textLower.contains("tramvay")) {
            poi = "İzmit Şehirlerarası Otobüs Terminali";
            foundDistrict = "İzmit";
        }

        // Önce Çok bilinen spesifik yerleri ara (Eğer üstteki özel kurallar bulmadıysa)
        if (poi.isEmpty()) {
            String[] bilinenNoktalar = {
                    "Şehir Hastanesi", "Seka Devlet Hastanesi", "Kocaeli Devlet Hastanesi", "Derince Eğitim ve Araştırma Hastanesi", "Umuttepe Hastanesi", "Sopalı Hastanesi", "Farabi Devlet Hastanesi", "Fatih Devlet Hastanesi",
                    "Umuttepe", "Seka Park", "Kent Meydanı", "Yürüyüş Yolu", "Fuar Alanı", "Ormanya", "Bilişim Vadisi", "Tübitak",
                    "Symbol AVM", "41 Burda", "Outlet Center", "Dolphin AVM", "Özdilek", "Arasta Park", "Ncity",
                    "Cengiz Topel", "Kartepe Kayak", "Kefken", "Kerpe", "Maşukiye", "Yuvacık",
                    // Bilinen Büyük Viyadükler ve Tüneller (TEM Otoyolu sorununu kökten çözer)
                    "Korutepe Viyadüğü", "Gültepe Viyadüğü", "Şirintepe Viyadüğü", "Bekirdere Viyadüğü", "Ertuğrul Gazi Viyadüğü", "Osmangazi Köprüsü", "Tavşancıl Viyadüğü", "Hereke Viyadüğü"
            };
            for (String n : bilinenNoktalar) {
                Pattern p = Pattern.compile("(?i)" + Pattern.quote(n));
                if (p.matcher(text).find()) {
                    poi = n;
                    // Eğer Akçaray veya İzmit içi bir yerse garantile
                    if (n.contains("Seka Park") || n.contains("Kent Meydanı") || n.contains("Şehir Hastanesi") || n.contains("Umuttepe") || n.contains("Viyadüğü")) {
                        if (n.contains("Tavşancıl") || n.contains("Hereke") || n.contains("Osmangazi")) {
                            foundDistrict = (n.contains("Osmangazi")) ? "Dilovası" : "Körfez";
                        } else {
                            foundDistrict = "İzmit";
                        }
                    }
                    break;
                }
            }
        }

        // Eğer manuel bulamadıysa özel regex ile durak, istasyon, viyadük, fabrika vb. ara
        if (poi.isEmpty()) {
            Matcher mPoi = Pattern.compile("([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ0-9'\\-]*\\s+){1,4}(?i)(Tramvay Durağı|Durağı|İstasyonu|Hastanesi|Polikliniği|Parkı|Kampüsü|Üniversitesi|AVM|Merkezi|Lisesi|Okulu|Tesisleri|Köprüsü|Viyadüğü|Viyadük|Tüneli|Tünel|Gişeleri|Plajı|Sahili|Fabrikası|Fabrika|Sanayi Sitesi)").matcher(text);
            if (mPoi.find()) {
                poi = mPoi.group(0).trim();
            }
        }

        // Karayolu, D100 vb. bul
        Matcher mYol = Pattern.compile("(?i)\\b(D-?100|E-?80|D-?130|Kuzey Marmara Otoyolu|Anadolu Otoyolu|TEM Otoyolu|TEM|D100)\\b").matcher(text);
        if (mYol.find()) {
            String yol = mYol.group(1).toUpperCase().replace(" ", "").replace("-", "");
            if (yol.contains("TEM") || yol.contains("E80") || yol.contains("ANADOLU")) {
                karayolu = "Anadolu Otoyolu";
            } else if (yol.contains("KUZEYMARMARA")) {
                karayolu = "Kuzey Marmara Otoyolu";
            } else if (yol.contains("130")) {
                karayolu = "D-130 Karayolu";
            } else {
                karayolu = "D-100 Karayolu";
            }
        }

        // Bulvar, Yol, Kavşak, Meydan, Site (Örn: "Kandıra Yolu", "Turan Güneş Bulvarı", "Sanayi Sitesi")
        Matcher mBulvar = Pattern.compile("([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]*\\s+){1,3}(?i)(Bulvarı|Yolu|Kavşağı|Mevkii|Sitesi|Meydanı)").matcher(text);
        if (mBulvar.find()) {
            bulvar = mBulvar.group(0).trim();
        }

        // Mahalle bul (Sadece Büyük harfle başlayan kelimeleri almasını sağlamak için)
        if (mahalle.isEmpty()) {
            Matcher mMahalle = Pattern.compile("([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]*\\s+){1,3}(?i)(Mahallesi|Mah\\.)").matcher(text);
            if (mMahalle.find()) {
                mahalle = mMahalle.group(0).replaceAll("(?i)Mah\\.", "Mahallesi").trim();
            } else {
                // Kalan bilinen küçük mahalle/bölge isimleri
                String[] ekMahalleler = {"Yenişehir", "Yenidoğan", "Sanayi"};
                for (String m : ekMahalleler) {
                    if (textLower.contains(m.toLowerCase())) {
                        mahalle = m + " Mahallesi";
                        break;
                    }
                }
            }
        }

        // Cadde bul
        Matcher mCadde = Pattern.compile("([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ]*\\s+){1,3}(?i)(Caddesi|Cadde|Cad\\.)").matcher(text);
        if (mCadde.find()) cadde = mCadde.group(0).replaceAll("(?i)Cad\\.", "Caddesi").trim();

        // Sokak bul
        Matcher mSokak = Pattern.compile("([A-ZÇĞİÖŞÜ0-9][a-zçğıöşüA-ZÇĞİÖŞÜ0-9]*\\s+){1,3}(?i)(Sokağı|Sokak|Sok\\.)").matcher(text);
        if (mSokak.find()) sokak = mSokak.group(0).replaceAll("(?i)Sok\\.", "Sokak").trim();

        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!poi.isEmpty()) parts.add(poi);
        if (!sokak.isEmpty()) parts.add(sokak);
        if (!cadde.isEmpty()) parts.add(cadde);
        if (!bulvar.isEmpty()) parts.add(bulvar);
        if (!karayolu.isEmpty()) parts.add(karayolu);
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
            // Parçalarına ayır
            java.util.List<String> list = new java.util.ArrayList<>(java.util.Arrays.asList(locationName.split(",\\s*")));

            String poi = "", sokak = "", cadde = "", bulvar = "", yol = "", mahalle = "", ilce = "";

            for(String p : list) {
                p = p.trim();
                if(p.equalsIgnoreCase("Kocaeli")) continue;

                String pLower = p.toLowerCase();
                if(pLower.matches(".*\\b(sokak|sokağı|sok\\.)\\b.*")) sokak = p;
                else if(pLower.matches(".*\\b(cadde|caddesi|cad\\.)\\b.*")) cadde = p;
                else if(pLower.matches(".*\\b(mahalle|mahallesi|mah\\.)\\b.*")) mahalle = p;
                else if(pLower.matches(".*\\b(bulvar|bulvarı)\\b.*")) bulvar = p;
                else if(pLower.matches(".*\\b(yolu|karayolu|otoyolu)\\b.*")) yol = p;
                else if(KOCAELI_DISTRICTS_CONTAINS(p)) ilce = p;
                else poi = p;
            }

            java.util.List<String> queriesToTry = new java.util.ArrayList<>();

            // 0. Tam metin
            String fullStr = locationName.endsWith("Kocaeli") ? locationName : locationName + ", Kocaeli";
            queriesToTry.add(fullStr);

            // 1. POI Odaklı
            if (!poi.isEmpty()) {
                String cleanPoi = poi.replaceAll("(?i)\\s+(Tramvay Durağı|Tramvay İstasyonu|Tramvay|Durağı|İstasyonu|Gişeleri|Tesisleri|Polikliniği|Hastanesi|Merkezi|Parkı|Viyadüğü|Viyadük|Tüneli|Tünel|Fabrikası|Fabrika|Sanayi Sitesi)", "").trim();

                if (!ilce.isEmpty()) queriesToTry.add(poi + ", " + ilce + ", Kocaeli");
                queriesToTry.add(poi + ", Kocaeli");
                if (!ilce.isEmpty()) queriesToTry.add(poi + " " + ilce + " Kocaeli"); // Boşlukla ayrılmış serbest arama

                if (!cleanPoi.isEmpty() && !cleanPoi.equals(poi)) {
                    if (!ilce.isEmpty()) queriesToTry.add(cleanPoi + ", " + ilce + ", Kocaeli");
                    queriesToTry.add(cleanPoi + ", Kocaeli");
                }
            }

            // 2. Sokak Odaklı
            if (!sokak.isEmpty()) {
                if (!mahalle.isEmpty() && !ilce.isEmpty()) queriesToTry.add(sokak + ", " + mahalle + ", " + ilce + ", Kocaeli");
                if (!ilce.isEmpty()) queriesToTry.add(sokak + ", " + ilce + ", Kocaeli");
                queriesToTry.add(sokak + ", Kocaeli");
                if (!ilce.isEmpty()) queriesToTry.add(sokak + " " + ilce + " Kocaeli");

                String cleanSokak = sokak.replaceAll("(?i)\\s+(Sokak|Sokağı|Sok\\.)", "").trim();
                if(!cleanSokak.isEmpty() && !ilce.isEmpty()) queriesToTry.add(cleanSokak + " Sokak, " + ilce + ", Kocaeli");
            }

            // 3. Cadde Odaklı
            if (!cadde.isEmpty()) {
                if (!mahalle.isEmpty() && !ilce.isEmpty()) queriesToTry.add(cadde + ", " + mahalle + ", " + ilce + ", Kocaeli");
                if (!ilce.isEmpty()) queriesToTry.add(cadde + ", " + ilce + ", Kocaeli");
                queriesToTry.add(cadde + ", Kocaeli");
                if (!ilce.isEmpty()) queriesToTry.add(cadde + " " + ilce + " Kocaeli");
            }

            // 4. Bulvar Öğesi
            if (!bulvar.isEmpty()) {
                if (!ilce.isEmpty()) queriesToTry.add(bulvar + ", " + ilce + ", Kocaeli");
                queriesToTry.add(bulvar + ", Kocaeli");
            }

            // 5. Yollar
            if (!yol.isEmpty()) {
                if (!ilce.isEmpty()) queriesToTry.add(yol + ", " + ilce + ", Kocaeli");
                queriesToTry.add(yol + ", Kocaeli");
                String cleanYol = yol.replaceAll("(?i)\\s+(Yolu|Karayolu)", "").trim();
                if(!cleanYol.isEmpty()) queriesToTry.add(cleanYol + ", Kocaeli");
            }

            // 6. Mahalle Odaklı
            if (!mahalle.isEmpty()) {
                if (!ilce.isEmpty()) queriesToTry.add(mahalle + ", " + ilce + ", Kocaeli");
                queriesToTry.add(mahalle + ", Kocaeli");

                String cleanMahalle = mahalle.replaceAll("(?i)\\s+(Mahallesi|Mah\\.)", "").trim();
                if(!cleanMahalle.isEmpty() && !ilce.isEmpty()) queriesToTry.add(cleanMahalle + " Mahallesi, " + ilce + ", Kocaeli");
                if(!cleanMahalle.isEmpty() && !ilce.isEmpty()) queriesToTry.add(cleanMahalle + ", " + ilce + ", Kocaeli");
                if(!cleanMahalle.isEmpty()) queriesToTry.add(cleanMahalle + " Kocaeli");
            }

            // 7. Sadece İlçe
            if (!ilce.isEmpty()) {
                queriesToTry.add(ilce + ", Kocaeli");
            }

            // Listeden benzersiz olanları sırayla sorgula
            java.util.List<String> tried = new java.util.ArrayList<>();
            for(String q : queriesToTry) {
                // Hatalı virgülleri temizle
                q = q.replaceAll("\\s+", " ").replaceAll(",\\s*,", ",").replaceAll("^,\\s*", "").trim();
                if(q.equals(", Kocaeli") || q.equals("Kocaeli") || q.isEmpty()) continue;

                if(tried.contains(q)) continue;
                tried.add(q);

                Thread.sleep(1000);
                log.info("📍 Nominatim Aranıyor: {}", q);
                LocationCoordinates result = callNominatimAPI(q, locationName);
                if (result != null) return result;
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
