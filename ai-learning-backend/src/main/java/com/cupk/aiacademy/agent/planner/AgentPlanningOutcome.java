package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentPlanningOutcome {
    private AgentDirectActionReply directReply;
    private String toolContext;

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
