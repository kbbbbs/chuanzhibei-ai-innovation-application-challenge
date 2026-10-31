package com.cupk.aiacademy.agent.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentActionConfirmRequest(
        String actionCode,
        Boolean confirmed,
        Map<String, Object> parameters,
        String actionId,
        String confirmationText
) {
    public AgentActionConfirmRequest(String actionCode, Boolean confirmed, Map<String, Object> parameters) {
        this(actionCode, confirmed, parameters, null, null);
    }

    public AgentActionConfirmRequest(String actionCode, Boolean confirmed, Map<String, Object> parameters, String actionId) {
        this(actionCode, confirmed, parameters, actionId, null);
    }

    public AgentActionConfirmRequest {
        actionCode = blankToNull(actionCode);
        actionId = blankToNull(actionId);
        confirmationText = blankToNull(confirmationText);
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
