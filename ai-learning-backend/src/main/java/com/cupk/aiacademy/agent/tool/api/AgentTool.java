package com.cupk.aiacademy.agent.tool.api;

import java.util.Set;

public interface AgentTool {
    String name();

    default Set<String> aliases() {
        return Set.of();
    }

    AgentToolResult execute(AgentToolContext context);
}
