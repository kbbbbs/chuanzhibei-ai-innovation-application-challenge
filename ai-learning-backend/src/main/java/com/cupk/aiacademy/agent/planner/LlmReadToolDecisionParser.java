package com.cupk.aiacademy.agent.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class LlmReadToolDecisionParser {
    private final ObjectMapper objectMapper;

    public LlmReadToolDecisionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LlmReadToolDecision parse(String modelText) {
        if (modelText == null || modelText.isBlank()) {
            throw new IllegalArgumentException("模型返回为空");
        }
        int start = modelText.indexOf('{');
        int end = modelText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型返回中未找到 JSON 对象");
        }
        try {
            return objectMapper.readValue(modelText.substring(start, end + 1), LlmReadToolDecision.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("模型返回不是合法的读工具决策 JSON", e);
        }
    }
}
