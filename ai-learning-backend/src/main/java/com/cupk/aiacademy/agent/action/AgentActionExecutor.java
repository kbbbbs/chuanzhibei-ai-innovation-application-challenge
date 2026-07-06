package com.cupk.aiacademy.agent.action;

public interface AgentActionExecutor {
    String actionCode();

    String actionName();

    default AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.MEDIUM;
    }

    default String confirmationText() {
        return "";
    }

    AgentActionExecutionResult execute(AgentActionExecutionContext context);
}
