package com.cupk.aiacademy.agent.action;

public record AgentActionSelectionResult(
        String actionCode,
        String toolName,
        double confidence,
        boolean needsClarification
) {
    public AgentActionSelectionResult {
        actionCode = actionCode == null ? "" : actionCode.trim();
        toolName = toolName == null ? "" : toolName.trim();
        if (confidence < 0) {
            confidence = 0;
        } else if (confidence > 1) {
            confidence = 1;
        }
    }
}
