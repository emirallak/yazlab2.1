package org.example.yazlab21.controller;

import lombok.RequiredArgsConstructor;
import org.example.yazlab21.config.GoogleMapsConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final GoogleMapsConfig googleMapsConfig;


    @GetMapping("/admin")
    public String adminPaneliniGoster() {

        return "forward:/admin-panel.html";
    }


    @GetMapping("/map")
    public String getMapPage() {

        return "forward:/map.html";
    }


    @GetMapping(value = "/api/config/maps-api-key", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> getGoogleMapsApiKey() {
        Map<String, String> response = new HashMap<>();
        response.put("apiKey", googleMapsConfig.getApiKey());
        return response;
    }
}