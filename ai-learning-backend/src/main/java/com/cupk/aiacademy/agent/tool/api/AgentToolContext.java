package com.cupk.aiacademy.agent.tool.api;

public record AgentToolContext(String query, int maxItems) {
    public String normalizedQuery() {
        return query == null ? "" : query.trim();
    }
}
