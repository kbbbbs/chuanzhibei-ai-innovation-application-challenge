package com.cupk.aiacademy.agent.action;

public record AgentActionValidationIssue(
        String field,
        String code,
        String message
) {
    public AgentActionValidationIssue {
        field = field == null ? "" : field.trim();
        code = code == null || code.isBlank() ? "invalid" : code.trim();
        message = message == null ? "" : message.trim();
    }
}
