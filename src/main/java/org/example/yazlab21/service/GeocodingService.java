package org.example.yazlab21.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GeocodingService {


    // Kocaeli'deki ana ilçeler
    private static final String[] KOCAELI_DISTRICTS = {
        "İzmit", "Körfez", "Derince", "Gölcük", "Başiskele", "Kandıra", 
        "Çayırova", "Dilovası", "Kartepe", "Gebze", "Darıca", "Pendik"
    };

    // Kocaeli'deki önemli mahallar ve mevkiiler
    private static final String[] KOCAELI_LOCATIONS = {
        "Karabaş", "Yeni Mahalle", "Eski Mahalle", "Osmangazi Köprüsü",
        "Antikkapı", "Kazım Karabekir", "Şehitler", "Doğantepe", 
        "Yığılcalı", "Akçaova", "Hereke", "Çifte Sabalar"
    };

    /**
     * Metinden konum bilgisini çıkarır
     */
    public String extractLocationFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // İlçe adlarını ara
        for (String district : KOCAELI_DISTRICTS) {
            if (text.toLowerCase().contains(district.toLowerCase())) {
                return district;
            }
        }

        // Mahalle adlarını ara
        for (String location : KOCAELI_LOCATIONS) {
            if (text.toLowerCase().contains(location.toLowerCase())) {
                return location;
            }
        }

        // Regex ile konum deseni ara (örn. "X. Cadde", "Y. Sokak")
        Pattern locationPattern = Pattern.compile(
            "(\\w+\\s+(Cadde|Caddesi|Sokak|Sokaği|Mahalle|Mah\\.|Köy|Mevki|Pasa|Pasha))\\s*[,.]*",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = locationPattern.matcher(text);

        if (matcher.find()) {
            String extractedLocation = matcher.group(1).trim();
            if (extractedLocation.length() > 3) {
                return extractedLocation;
            }
        }

        return null;
    }

    /**
     * Kocaeli'ye ait bilinir konumlar için önceden tanımlanmış koordinatlar
     */
    public LocationCoordinates getKocaeliLocationCoordinates(String locationName) {
        if (locationName == null) {
            return null;
        }

        // Önceden tanımlanmış koordinatlar (gerçek değerler)
        String lowerName = locationName.toLowerCase();
        
        if (lowerName.contains("izmit")) {
            return new LocationCoordinates("İzmit", 40.7671, 29.9427, "İzmit, Kocaeli");
        } else if (lowerName.contains("körfez")) {
            return new LocationCoordinates("Körfez", 40.8333, 29.8500, "Körfez, Kocaeli");
        } else if (lowerName.contains("derince")) {
            return new LocationCoordinates("Derince", 40.8222, 29.7833, "Derince, Kocaeli");
        } else if (lowerName.contains("gölcük")) {
            return new LocationCoordinates("Gölcük", 40.7333, 29.7500, "Gölcük, Kocaeli");
        } else if (lowerName.contains("başiskele")) {
            return new LocationCoordinates("Başiskele", 40.8167, 29.6667, "Başiskele, Kocaeli");
        } else if (lowerName.contains("kandıra")) {
            return new LocationCoordinates("Kandıra", 40.9833, 30.2500, "Kandıra, Kocaeli");
        } else if (lowerName.contains("çayırova")) {
            return new LocationCoordinates("Çayırova", 40.8833, 29.6333, "Çayırova, Kocaeli");
        } else if (lowerName.contains("dilovası")) {
            return new LocationCoordinates("Dilovası", 40.8000, 29.5000, "Dilovası, Kocaeli");
        } else if (lowerName.contains("kartepe")) {
            return new LocationCoordinates("Kartepe", 40.8500, 29.7000, "Kartepe, Kocaeli");
        } else if (lowerName.contains("gebze")) {
            return new LocationCoordinates("Gebze", 40.7667, 29.4500, "Gebze, Kocaeli");
        } else if (lowerName.contains("osmangazi köprüsü")) {
            return new LocationCoordinates("Osmangazi Köprüsü", 40.8458, 29.4825, "Osmangazi Köprüsü");
        } else if (lowerName.contains("antikkapı")) {
            return new LocationCoordinates("Antikkapı", 40.7600, 29.9600, "Antikkapı, İzmit");
        } else if (lowerName.contains("karabaş")) {
            return new LocationCoordinates("Karabaş", 40.7650, 29.9500, "Karabaş, İzmit");
        } else if (lowerName.contains("hereke")) {
            return new LocationCoordinates("Hereke", 40.8250, 29.7200, "Hereke, Körfez");
        }

        // Eşleşme yoksa null dön
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



