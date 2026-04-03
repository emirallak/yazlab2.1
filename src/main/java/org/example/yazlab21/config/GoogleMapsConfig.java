package org.example.yazlab21.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Getter
public class GoogleMapsConfig {
    
    @Value("${GOOGLE_MAPS_API_KEY}")
    private String apiKey;
}

