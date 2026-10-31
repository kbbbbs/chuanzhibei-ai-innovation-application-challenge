package com.cupk.aiacademy.agent.action;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted, session-scoped state for a write action that still needs user input.
 */
public record AgentActionClarificationDraft(
        String actionCode,
        String originalRequest,
        Map<String, Object> normalizedArguments,
        List<String> missingRequiredFields,
        long createdAtEpochMillis,
        long expiresAtEpochMillis
) {
    public AgentActionClarificationDraft {
        actionCode = actionCode == null ? "" : actionCode.trim();
        originalRequest = originalRequest == null ? "" : originalRequest.trim();
        normalizedArguments = normalizedArguments == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(normalizedArguments));
        missingRequiredFields = missingRequiredFields == null
                ? List.of()
                : List.copyOf(missingRequiredFields);
        createdAtEpochMillis = Math.max(0, createdAtEpochMillis);
        expiresAtEpochMillis = Math.max(createdAtEpochMillis, expiresAtEpochMillis);
    }

    public static AgentActionClarificationDraft create(String actionCode,
                                                       String originalRequest,
                                                       Map<String, Object> normalizedArguments,
                                                       List<String> missingRequiredFields,
                                                       Duration ttl) {
        long createdAt = Instant.now().toEpochMilli();
        Duration safeTtl = ttl == null || ttl.isZero() || ttl.isNegative()
                ? AgentActionSpec.DEFAULT_PENDING_TTL
                : ttl;
        return new AgentActionClarificationDraft(
                actionCode,
                originalRequest,
                normalizedArguments,
                missingRequiredFields,
                createdAt,
                createdAt + safeTtl.toMillis()
        );
    }

    @JsonIgnore
    public boolean isExpired() {
        return expiresAtEpochMillis <= Instant.now().toEpochMilli();
    }
}
