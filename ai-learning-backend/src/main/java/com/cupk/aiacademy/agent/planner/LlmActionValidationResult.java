package com.cupk.aiacademy.agent.planner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmActionValidationResult {
    private boolean valid;

    private String actionCode;

    private Map<String, Object> normalizedArguments;

    private List<String> missingRequiredFields;

    private List<String> errors;

    public static LlmActionValidationResult invalid(List<String> errors) {
        return new LlmActionValidationResult(false, "", Map.of(), List.of(), errors);
    }
}