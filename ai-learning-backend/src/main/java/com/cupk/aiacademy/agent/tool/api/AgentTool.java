package com.cupk.aiacademy.agent.tool.api;

import com.cupk.aiacademy.agent.action.AgentActionSpec;

import java.util.Optional;
import java.util.Set;

public interface AgentTool {
    String name();

    default Set<String> aliases() {
        return Set.of();
    }

    default AgentToolOperationType declaredOperationType() {
        if (getClass().getSimpleName().endsWith("PlanTool") && !"learning_plan".equals(name())) {
            return AgentToolOperationType.WRITE_PLAN;
        }
        return AgentToolOperationType.READ;
    }

    default boolean promptContextEnabled() {
        return declaredOperationType() == AgentToolOperationType.READ;
    }

    default Optional<AgentActionSpec> actionSpec() {
        return Optional.empty();
    }

    AgentToolResult execute(AgentToolContext context);
}
