package com.cupk.aiacademy.agent.tool.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentToolContext(String query, int maxItems, Map<String, Object> arguments) {
    public AgentToolContext(String query, int maxItems) {
        this(query, maxItems, Map.of());
    }

    public AgentToolContext {
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    public String normalizedQuery() {
        return query == null ? "" : query.trim();
    }

    public String stringArgument(String name) {
        Object value = arguments.get(name);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public Integer integerArgument(String name) {
        Object value = arguments.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public Boolean booleanArgument(String name) {
        Object value = arguments.get(name);
        return value instanceof Boolean bool ? bool : null;
    }
}
