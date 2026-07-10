package com.cupk.aiacademy.agent.planner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmReadToolValidationResult {
    private boolean valid;
    private boolean chat;
    private String toolName;
    private Map<String, Object> normalizedArguments;
    private List<String> errors;

    public static LlmReadToolValidationResult invalid(List<String> errors) {
        return new LlmReadToolValidationResult(false, false, "", Map.of(),
                errors == null ? List.of() : List.copyOf(errors));
    }

    public static LlmReadToolValidationResult chat() {
        return new LlmReadToolValidationResult(true, true, "", Map.of(), List.of());
    }

    public void setNormalizedArguments(Map<String, Object> normalizedArguments) {
        this.normalizedArguments = normalizedArguments == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(normalizedArguments));
    }

    public String getQuery() {
        Object value = normalizedArguments == null ? null : normalizedArguments.get("query");
        return value == null ? "" : String.valueOf(value).trim();
    }

    public int getMaxItems() {
        Object value = normalizedArguments == null ? null : normalizedArguments.get("maxItems");
        return value instanceof Number number ? number.intValue() : 10;
    }
}
