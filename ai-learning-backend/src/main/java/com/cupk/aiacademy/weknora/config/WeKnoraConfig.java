package com.cupk.aiacademy.weknora.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "weknora")
public class WeKnoraConfig {
    private String baseUrl = "http://localhost:8090";
    private String apiKey;
    private String storagePath = "uploads/knowledge";
}
