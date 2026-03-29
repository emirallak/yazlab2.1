package org.example.yazlab21.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SimilarityService {

    /**
     * Levenshtein Distance kullanarak iki string'in benzerliğini hesaplar
     * Dönen değer: 0 (tamamen farklı) ile 1.0 (tamamen aynı) arasında
     */
    public double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }

        if (str1.equals(str2)) {
            return 1.0;
        }

        int maxLength = Math.max(str1.length(), str2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        // StringUtils.getLevenshteinDistance deprecated olduğu için manuel hesaplıyoruz
        int distance = levenshteinDistance(str1, str2);
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Levenshtein Distance manuel hesaplama
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] d = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }

        return d[len1][len2];
    }

    /**
     * Başlık ve içeriğe göre iki haberin benzerliğini hesaplar
     * %90 ve üzeri benzerlik olması gerekir
     */
    public double calculateNewsItemSimilarity(String baslik1, String baslik2) {
        // Başlıkları normalize et (case-insensitive, özel karakterler)
        String normalized1 = normalizeText(baslik1);
        String normalized2 = normalizeText(baslik2);

        return calculateSimilarity(normalized1, normalized2);
    }

    /**
     * Metin normalleştirmesi (case-insensitive, trim, vb)
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Benzer haberleri filtreler (%90 benzerlik eşiği)
     */
    public List<String> filterSimilarNews(String referenceTitle, List<String> titles, double threshold) {
        return titles.stream()
            .filter(title -> calculateNewsItemSimilarity(referenceTitle, title) >= threshold)
            .collect(Collectors.toList());
    }

    /**
     * Benzer haberlerin içinde en spesifik konum bilgisine sahip olanı bulur
     */
    public String findMostSpecificLocation(List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return null;
        }

        // Konum spesifikliği: daha uzun konum açıklaması = daha spesifik
        return locations.stream()
            .filter(loc -> loc != null && !loc.isEmpty())
            .max((loc1, loc2) -> {
                // Önce kelime sayısına göre (mahalle + sokak > sadece ilçe)
                int words1 = loc1.split("\\s+").length;
                int words2 = loc2.split("\\s+").length;
                if (words1 != words2) {
                    return Integer.compare(words1, words2);
                }
                // Eşitse, daha uzun olanı seç
                return Integer.compare(loc1.length(), loc2.length());
            })
            .orElse(null);
    }

    /**
     * Editöryal Uzaklığı Basit Versiyonu (daha hızlı)
     */
    public double calculateCosineSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null || str1.isEmpty() || str2.isEmpty()) {
            return 0.0;
        }

        String normalized1 = normalizeText(str1);
        String normalized2 = normalizeText(str2);

        String[] words1 = normalized1.split(" ");
        String[] words2 = normalized2.split(" ");

        int commonWords = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equals(word2)) {
                    commonWords++;
                    break;
                }
            }
        }

        int totalWords = Math.max(words1.length, words2.length);
        if (totalWords == 0) {
            return 0.0;
        }

        return (double) commonWords / totalWords;
    }
}

