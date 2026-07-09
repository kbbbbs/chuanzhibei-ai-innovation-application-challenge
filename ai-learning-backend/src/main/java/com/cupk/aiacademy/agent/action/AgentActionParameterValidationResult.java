package com.cupk.aiacademy.agent.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentActionParameterValidationResult(
        Map<String, Object> parameters,
        List<String> missingRequiredFields,
        List<AgentActionValidationIssue> issues
) {
    public AgentActionParameterValidationResult {
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        missingRequiredFields = missingRequiredFields == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(missingRequiredFields));
        issues = issues == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public boolean valid() {
        return missingRequiredFields.isEmpty() && issues.isEmpty();
    }

    public List<Map<String, Object>> issueData() {
        if (issues.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (AgentActionValidationIssue issue : issues) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", issue.field());
            item.put("code", issue.code());
            item.put("message", issue.message());
            data.add(item);
        }
        return Collections.unmodifiableList(data);
    }
}
