package com.cupk.aiacademy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Four runtime capabilities. Credentials are deliberately excluded from diagnostics. */
@Getter
@Setter
@ConfigurationProperties(prefix = "runtime.models")
public class RuntimeModelProperties {
    private Endpoint generation = new Endpoint();
    private Endpoint planning = new Endpoint();
    private Endpoint embedding = new Endpoint();
    private Endpoint ocr = new Endpoint();

    public Endpoint endpoint(String role) {
        return switch (role) {
            case "generation" -> generation;
            case "planning" -> planning;
            case "embedding" -> embedding;
            case "ocr" -> ocr;
            default -> throw new IllegalArgumentException("Unknown model role");
        };
    }

    @Getter
    @Setter
    public static class Endpoint {
        private boolean enabled = true;
        private String baseUrl = "";
        @JsonIgnore
        private String apiKey = "";
        private String model = "";
        private int timeoutSeconds = 30;
        private int contextWindowTokens = 0; // Declared by operator; not inferred from a small probe.
        public String getBaseUrl() { return clean(baseUrl); }
        @JsonIgnore public String getApiKey() { return clean(apiKey); }
        public String getModel() { return clean(model); }
        private static String clean(String value) { return value == null ? "" : value.strip(); }
        @Override public String toString() { return "Runtime model settings [credentials hidden]"; }
    }
}
