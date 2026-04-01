package org.example.yazlab21.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Google Maps API konfigürasyonu
 */
@Component
@Getter
public class GoogleMapsConfig {
    
    @Value("${google.maps.api.key}")
    private String apiKey;
}

