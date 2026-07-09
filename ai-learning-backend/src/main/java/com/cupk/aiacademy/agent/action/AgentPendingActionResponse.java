package com.cupk.aiacademy.agent.action;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentPendingActionResponse(
        String actionId,
        String actionCode,
        String actionName,
        String domain,
        String riskLevel,
        Boolean requiresConfirmation,
        String confirmationText,
        String summary,
        Integer createdBy,
        Map<String, Object> parameters,
        List<String> impacts,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        boolean expired
) {
    public AgentPendingActionResponse {
        parameters = parameters == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        impacts = impacts == null ? null : Collections.unmodifiableList(new ArrayList<>(impacts));
    }

    public static AgentPendingActionResponse fromDomain(AgentPendingAction action,
                                                        AgentActionExecutor executor,
                                                        boolean includeParameters) {
        if (action == null) {
            return null;
        }
        String actionCode = text(action.actionCode());
        String actionName = executor == null ? actionCode : text(executor.actionName());
        String riskLevel = executor == null ? null : executor.riskLevel().name();
        String confirmationText = executor == null ? null : emptyToNull(executor.confirmationText());
        Map<String, Object> parameters = includeParameters ? action.parameters() : null;
        return new AgentPendingActionResponse(
                action.actionId(),
                actionCode,
                actionName,
                domainFromActionCode(actionCode),
                riskLevel,
                true,
                confirmationText,
                null,
                action.createdBy(),
                parameters,
                null,
                action.createdAt(),
                action.expiresAt(),
                action.isExpired(LocalDateTime.now())
        );
    }

    public static AgentPendingActionResponse fromToolData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String actionId = text(data.get("actionId"));
        if (actionId.isBlank()) {
            return null;
        }
        String actionCode = text(data.get("actionCode"));
        LocalDateTime expiresAt = parseLocalDateTime(data.get("expiresAt"));
        return new AgentPendingActionResponse(
                actionId,
                actionCode,
                emptyToNull(text(data.get("actionName"))),
                firstNotBlank(text(data.get("domain")), domainFromActionCode(actionCode)),
                emptyToNull(text(data.get("riskLevel"))),
                bool(data.get("requiresConfirmation")),
                emptyToNull(text(data.get("confirmationText"))),
                emptyToNull(text(data.get("summary"))),
                integer(data.get("createdBy")),
                map(data.get("parameters")),
                list(data.get("impacts")),
                parseLocalDateTime(data.get("createdAt")),
                expiresAt,
                expiresAt != null && LocalDateTime.now().isAfter(expiresAt)
        );
    }

    private static String domainFromActionCode(String actionCode) {
        if (actionCode == null || actionCode.isBlank()) {
            return null;
        }
        int dotIndex = actionCode.indexOf('.');
        return dotIndex <= 0 ? actionCode : actionCode.substring(0, dotIndex);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNotBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Boolean bool(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static LocalDateTime parseLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        String text = text(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null) {
                copy.put(String.valueOf(key), item);
            }
        });
        return copy;
    }

    private static List<String> list(Object value) {
        if (!(value instanceof Iterable<?> source)) {
            return null;
        }
        List<String> copy = new ArrayList<>();
        for (Object item : source) {
            if (item != null) {
                copy.add(String.valueOf(item));
            }
        }
        return copy;
    }
}
