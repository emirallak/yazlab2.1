package org.example.yazlab21.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    // Kullanıcı tarayıcıya "/admin" yazdığında bu metod tetiklenir
    @GetMapping("/admin")
    public String adminPaneliniGoster() {
        // static klasörünün içindeki admin-panel.html dosyasına yönlendir (forward)
        return "forward:/admin-panel.html";
    }

    // Harita sayfası
    @GetMapping("/map")
    public String getMapPage() {
        // static klasörünün içindeki map.html dosyasına yönlendir (forward)
        return "forward:/map.html";
    }
}