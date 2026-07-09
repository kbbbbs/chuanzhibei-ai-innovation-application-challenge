package com.cupk.aiacademy.agent.planner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmActionPlanResult {
    private LlmActionDecision decision;

    private LlmActionValidationResult validation;
}