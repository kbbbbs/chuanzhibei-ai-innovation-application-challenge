package com.cupk.aiacademy.agent.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String provider = "openai-compatible";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4.1-mini";
    private Double temperature = 0.7;
}
