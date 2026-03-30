package org.example.yazlab21.util;

import org.jsoup.nodes.Document;

import java.util.List;

public final class ContentExtractor {

    private ContentExtractor() {
    }

    /**
     * Haber sayfasından yorum/ilgili haber/reklam vb. içerikleri temizleyip ana metni döner.
     */
    public static String extractMainText(Document doc, List<String> preferredSelectors, String fallback) {
        if (doc == null) return safe(fallback);

        // Gürültülü alanları temizle (yorum, paylaşım, video, reklam, ilgili haber)
        doc.select("script, style, noscript, iframe, video, audio, source, figure, nav, header, footer, .comments, #comments, .comment, .comment-list, .related, .related-post, .related-articles, .related-news, .recommended, .share, .share-bar, .social-share, .post-share, .social, .tags, .tag-cloud, .author, .breadcrumb, .ads, .advert, .banner, .gallery, .slider, .video, .video-player, .video-container, .player, .jwplayer, .plyr, .youtube, .embed, [class*=comment], [id*=comment], [class*=yorum]").remove();

        // Öncelikli seçicilerle dene
        if (preferredSelectors != null) {
            for (String selector : preferredSelectors) {
                String text = doc.select(selector).text();
                if (!text.isBlank()) {
                    return cleanupText(text);
                }
            }
        }

        // Yaygın gövde seçicileriyle bir şans daha ver
        String common = doc.select("article, main, .article-body, .news-content, .post-content, .content, .entry-content, .article-content").text();
        if (!common.isBlank()) {
            return cleanupText(common);
        }

        return cleanupText(fallback);
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static String cleanupText(String raw) {
        String text = safe(raw);

        // Tek satırlık video player açıklamaları, paylaş/abone satırları, hashtag etiketleri
        text = text.replaceAll("(?is)video player is loading.*?fullscreen", " ");
        text = text.replaceAll("(?is)this is a modal window.*?end of dialog window", " ");
        text = text.replaceAll("(?i)(play video|mute|current time|duration)\s*[0-9:./-]*", " ");
        text = text.replaceAll("(?i)(paylaş|twitle|whatsapp|abone ol)", " ");
        text = text.replaceAll("#\s*[\\p{L}0-9çğıöşüÇĞİÖŞÜ_,.-]+", " ");

        // Fazla boşlukları temizle
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}
