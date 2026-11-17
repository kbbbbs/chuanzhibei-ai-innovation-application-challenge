package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.pojo.AgentDefinition;
import org.springframework.stereotype.Component;

@Component
public class UnifiedAgentPlanner {
    private final LlmIntentSelector intentSelector;
    private final SchemaDrivenAgentPlanner writePlanner;
    private final SchemaDrivenReadToolPlanner readPlanner;

    public UnifiedAgentPlanner(LlmIntentSelector intentSelector,
                               SchemaDrivenAgentPlanner writePlanner,
                               SchemaDrivenReadToolPlanner readPlanner) {
        this.intentSelector = intentSelector;
        this.writePlanner = writePlanner;
        this.readPlanner = readPlanner;
    }

    public AgentPlanningOutcome plan(AgentDefinition agent, String userText) {
        AgentIntentSelection selection = intentSelector.select(agent, userText);
        return switch (selection.getType()) {
            case CHAT -> AgentPlanningOutcome.chat();
            case READ -> "learning_analysis_report".equals(selection.getCode())
                    ? readPlanner.planSelectedToolOutcome(agent, userText, selection.getCode())
                    : AgentPlanningOutcome.read(readPlanner.planSelectedToolContext(agent, userText, selection.getCode()));
            case WRITE -> {
                java.util.Optional<AgentDirectActionReply> reply = writePlanner
                        .tryPlanSelected(agent, userText, selection.getCode());
                yield reply.map(AgentPlanningOutcome::write)
                        .orElseGet(AgentPlanningOutcome::chat);
            }
        };
    }
}
