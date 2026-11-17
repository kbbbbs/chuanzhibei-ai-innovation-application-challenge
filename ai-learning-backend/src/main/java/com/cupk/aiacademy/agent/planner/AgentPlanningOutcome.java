package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import lombok.Data;

@Data
public class AgentPlanningOutcome {
    private AgentDirectActionReply directReply;
    private String toolContext;
    private AgentToolResult readToolResult;

    public AgentPlanningOutcome(AgentDirectActionReply directReply, String toolContext) {
        this.directReply = directReply;
        this.toolContext = toolContext;
    }

    public static AgentPlanningOutcome readResult(AgentToolResult result) {
        var outcome = read("[" + result.toolName() + "]\n" + result.content());
        outcome.setReadToolResult(result);
        return outcome;
    }

    public static AgentPlanningOutcome chat() {
        return new AgentPlanningOutcome(null, "");
    }

    public static AgentPlanningOutcome read(String toolContext) {
        return new AgentPlanningOutcome(null, toolContext == null ? "" : toolContext);
    }

    public static AgentPlanningOutcome write(AgentDirectActionReply directReply) {
        return new AgentPlanningOutcome(directReply, "");
    }
}
