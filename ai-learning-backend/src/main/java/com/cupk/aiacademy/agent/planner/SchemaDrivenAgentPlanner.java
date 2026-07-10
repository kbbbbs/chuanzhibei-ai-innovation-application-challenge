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
import java.util.Locale;
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
            log.warn("Schema-driven agent planner failed, continue without a write plan: message={}", e.getMessage(), e);
            return Optional.empty();
        }

        if (result == null || result.getValidation() == null) {
            return Optional.empty();
        }

        if (isChatDecision(result.getDecision())) {
            return Optional.empty();
        }

        if (!result.getValidation().isValid()) {
            log.debug("Schema-driven agent planner requires user input: errors={}",
                    result.getValidation().getErrors());
            return Optional.of(clarificationReply(result));
        }

        return Optional.of(planFactory.create(userText.trim(), result.getValidation()));
    }

    private boolean isChatDecision(LlmActionDecision decision) {
        return decision != null
                && decision.getDecision() != null
                && "CHAT".equals(decision.getDecision().trim().toUpperCase(Locale.ROOT));
    }

    private AgentDirectActionReply clarificationReply(LlmActionPlanResult result) {
        LlmActionDecision decision = result.getDecision();
        LlmActionValidationResult validation = result.getValidation();
        String message = decision == null ? null : decision.getUserMessage();
        if (message == null || message.isBlank()) {
            List<String> missing = validation.getMissingRequiredFields();
            if (missing != null && !missing.isEmpty()) {
                message = "还需要你补充以下信息：" + String.join("、", missing) + "。";
            } else {
                message = "暂时无法生成可靠的操作计划，请补充更明确的操作对象和参数。";
            }
        }
        return new AgentDirectActionReply(message.trim(), null);
    }
}
