package com.cupk.aiacademy.agent.action;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentActionExecutionResult(
        String actionCode,
        String actionName,
        boolean success,
        String message,
        Map<String, Object> data,
        LocalDateTime executedAt
) {
    public AgentActionExecutionResult {
        actionCode = actionCode == null ? "" : actionCode;
        actionName = actionName == null ? actionCode : actionName;
        message = message == null ? "" : message;
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        executedAt = executedAt == null ? LocalDateTime.now() : executedAt;
    }
}
