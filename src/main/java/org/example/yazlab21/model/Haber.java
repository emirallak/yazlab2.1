package org.example.yazlab21.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "haberler")

public class Haber
{

    @Id
    private String id;

    private String haberTuru;
    private String baslik;
    private String icerik;


    private String konumMetni;
    private Double enlem;
    private Double boylam;

    private LocalDateTime yayinTarihi;
    private String kaynakAd;
    private String link;
}