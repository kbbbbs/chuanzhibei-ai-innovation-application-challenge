package com.cupk.aiacademy.weknora.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "weknora")
public class WeKnoraConfig {
    private String baseUrl = "http://localhost:8091";
    private String apiKey;
    private String storagePath = "uploads/knowledge";

    /** Returns the normalized base URL without trailing /api/v1 or /api, since API paths are appended by the service. */
    public String getBaseUrl() {
        if (baseUrl == null) return "http://localhost:8091";
        String url = baseUrl.replaceAll("/api/v1/?$", "").replaceAll("/api/?$", "");
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
