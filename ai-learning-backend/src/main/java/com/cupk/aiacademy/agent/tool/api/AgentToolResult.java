package com.cupk.aiacademy.agent.tool.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentToolResult(
        String toolName,
        AgentToolOperationType operationType,
        boolean success,
        String content,
        Map<String, Object> data
) {
    public AgentToolResult {
        operationType = operationType == null ? AgentToolOperationType.READ : operationType;
        content = content == null ? "" : content;
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public static AgentToolResult read(String content) {
        return new AgentToolResult(null, AgentToolOperationType.READ, true, content, Map.of());
    }

    public static AgentToolResult read(String toolName, String content, Map<String, Object> data) {
        return new AgentToolResult(toolName, AgentToolOperationType.READ, true, content, data);
    }

    public static AgentToolResult writePlan(String toolName, String content, Map<String, Object> data) {
        return new AgentToolResult(toolName, AgentToolOperationType.WRITE_PLAN, true, content, data);
    }

    public static AgentToolResult writeExecute(String toolName, String content, Map<String, Object> data) {
        return new AgentToolResult(toolName, AgentToolOperationType.WRITE_EXECUTE, true, content, data);
    }

    public static AgentToolResult failure(String toolName, AgentToolOperationType operationType, String content) {
        return new AgentToolResult(toolName, operationType, false, content, Map.of());
    }

    public AgentToolResult withToolName(String defaultToolName) {
        if (toolName != null && !toolName.isBlank()) {
            return this;
        }
        return new AgentToolResult(defaultToolName, operationType, success, content, data);
    }

    public boolean hasContent() {
        return content != null && !content.trim().isEmpty();
    }
}
