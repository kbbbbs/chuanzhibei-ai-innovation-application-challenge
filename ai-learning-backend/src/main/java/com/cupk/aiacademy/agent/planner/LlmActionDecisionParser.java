package com.cupk.aiacademy.agent.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class LlmActionDecisionParser {
    private final ObjectMapper objectMapper;

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型返回中未找到 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    public LlmActionDecision parse(String modelText) {
        if (modelText == null || modelText.trim().isEmpty()) {
            throw new IllegalArgumentException("模型返回为空");
        }

        String json = extractJsonObject(modelText.trim());
        try {
            return objectMapper.readValue(json, LlmActionDecision.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("模型返回不是合法 LlmActionDecision JSON", e);
        }
    }

}