package com.cupk.aiacademy.agent.planner;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LlmActionPlanResult {
    private LlmActionDecision decision;

    private LlmActionValidationResult validation;

    private int attempts;

    private boolean exhausted;

    public LlmActionPlanResult(LlmActionDecision decision,
                               LlmActionValidationResult validation) {
        this(decision, validation, 1, false);
    }

    public LlmActionPlanResult(LlmActionDecision decision,
                               LlmActionValidationResult validation,
                               int attempts,
                               boolean exhausted) {
        this.decision = decision;
        this.validation = validation;
        this.attempts = Math.max(0, attempts);
        this.exhausted = exhausted;
    }
}
