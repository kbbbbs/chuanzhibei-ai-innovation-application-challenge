package com.cupk.aiacademy.agent.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentParameterExtractionResult(
        Map<String, Object> parameters,
        List<String> missingRequiredFields,
        boolean modelAttempted,
        boolean modelSucceeded,
        String source,
        List<AgentActionValidationIssue> validationIssues
) {
    public AgentParameterExtractionResult(Map<String, Object> parameters,
                                          List<String> missingRequiredFields,
                                          boolean modelAttempted,
                                          boolean modelSucceeded,
                                          String source) {
        this(parameters, missingRequiredFields, modelAttempted, modelSucceeded, source, List.of());
    }

    public AgentParameterExtractionResult {
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        missingRequiredFields = missingRequiredFields == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(missingRequiredFields));
        source = source == null || source.isBlank() ? "none" : source.trim();
        validationIssues = validationIssues == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(validationIssues));
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", source);
        data.put("modelAttempted", modelAttempted);
        data.put("modelSucceeded", modelSucceeded);
        if (!validationIssues.isEmpty()) {
            List<Map<String, Object>> issues = new ArrayList<>();
            for (AgentActionValidationIssue issue : validationIssues) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("field", issue.field());
                item.put("code", issue.code());
                item.put("message", issue.message());
                issues.add(item);
            }
            data.put("validationIssues", Collections.unmodifiableList(issues));
        }
        return data;
    }
}
