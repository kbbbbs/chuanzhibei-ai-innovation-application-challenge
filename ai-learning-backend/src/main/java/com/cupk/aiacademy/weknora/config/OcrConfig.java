package com.cupk.aiacademy.weknora.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ocr")
public class OcrConfig {
    private boolean enabled = true;
    private TesseractConfig tesseract = new TesseractConfig();
    private int minTextLength = 50;

    @Data
    public static class TesseractConfig {
        private String dataPath = "";
        private String language = "chi_sim+eng";
    }
}
