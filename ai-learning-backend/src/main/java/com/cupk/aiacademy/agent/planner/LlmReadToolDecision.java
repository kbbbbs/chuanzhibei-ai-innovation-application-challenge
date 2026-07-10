package com.cupk.aiacademy.agent.planner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmReadToolDecision {
    private String decision;
    private String toolName;
    private Map<String, Object> arguments;
    private String userMessage;
    private Double confidence;
}
