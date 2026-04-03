package org.example.yazlab21;

import io.github.cdimascio.dotenv.Dotenv; // Eğer bu kütüphane varsa
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Yazlab21Application {

    public static void main(String[] args) {
        // .env dosyasını manuel yükle ve sistem özelliklerine bas
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(Yazlab21Application.class, args);
    }
}