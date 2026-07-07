package com.cupk.aiacademy.agent.action;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentPendingAction(
        String actionId,
        String actionCode,
        Integer createdBy,
        Map<String, Object> parameters,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public AgentPendingAction {
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && (now == null ? LocalDateTime.now() : now).isAfter(expiresAt);
    }
}
