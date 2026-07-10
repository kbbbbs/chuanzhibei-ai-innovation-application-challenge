package com.cupk.aiacademy.agent.planner;

import lombok.Data;

@Data
public class LlmIntentDecision {
    private String decision;
    private String code;
    private Double confidence;
}
