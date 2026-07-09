package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SchemaDrivenAgentPlanner {
    private static final Logger log = LoggerFactory.getLogger(SchemaDrivenAgentPlanner.class);

    private final AgentPlannerProperties properties;
    private final AgentConfigurationParser configurationParser;
    private final AgentActionCatalog actionCatalog;
    private final LlmActionRepairLoop repairLoop;
    private final LlmValidatedPlanFactory planFactory;

    public SchemaDrivenAgentPlanner(AgentPlannerProperties properties,
                                    ObjectMapper objectMapper,
                                    AgentActionCatalog actionCatalog,
                                    LlmActionRepairLoop repairLoop,
                                    LlmValidatedPlanFactory planFactory) {
        this.properties = properties;
        this.configurationParser = new AgentConfigurationParser(objectMapper);
        this.actionCatalog = actionCatalog;
        this.repairLoop = repairLoop;
        this.planFactory = planFactory;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isFallbackRouterEnabled() {
        return properties.isFallbackRouterEnabled();
    }

    public Optional<AgentDirectActionReply> tryPlan(AgentDefinition agent, String userText) {
        if (!properties.isEnabled() || agent == null || userText == null || userText.isBlank()) {
            return Optional.empty();
        }

        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        List<AgentActionSpec> specs = actionCatalog.enabledSpecs(enabledTools);
        if (specs.isEmpty()) {
            return Optional.empty();
        }

        LlmActionPlanResult result;
        try {
            result = repairLoop.plan(userText.trim(), specs);
        } catch (Exception e) {
            log.warn("Schema-driven agent planner failed, fallback to rule/model path: message={}", e.getMessage(), e);
            return Optional.empty();
        }

        if (result == null || result.getValidation() == null || !result.getValidation().isValid()) {
            if (result != null && result.getValidation() != null) {
                log.debug("Schema-driven agent planner did not produce a valid action: errors={}",
                        result.getValidation().getErrors());
            }
            return Optional.empty();
        }

        return Optional.of(planFactory.create(userText.trim(), result.getValidation()));
    }
}
