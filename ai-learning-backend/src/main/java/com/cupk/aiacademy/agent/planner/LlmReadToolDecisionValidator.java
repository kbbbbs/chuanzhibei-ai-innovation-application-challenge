package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionParameterValidationResult;
import com.cupk.aiacademy.agent.action.AgentActionSchemaValidator;
import com.cupk.aiacademy.agent.action.AgentActionValidationIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LlmReadToolDecisionValidator {
    private final AgentPlannerProperties properties;
    private final AgentActionSchemaValidator schemaValidator;

    public LlmReadToolDecisionValidator(AgentPlannerProperties properties,
                                        AgentActionSchemaValidator schemaValidator) {
        this.properties = properties;
        this.schemaValidator = schemaValidator;
    }

    public LlmReadToolValidationResult validate(LlmReadToolDecision decision,
                                                List<AgentReadToolSpec> enabledSpecs) {
        if (decision == null) {
            return LlmReadToolValidationResult.invalid(List.of("模型未返回读工具决策"));
        }
        String decisionType = upper(decision.getDecision());
        if ("CHAT".equals(decisionType)) {
            return LlmReadToolValidationResult.chat();
        }

        List<String> errors = new ArrayList<>();
        if (!"TOOL".equals(decisionType)) {
            errors.add("decision 必须是 TOOL 或 CHAT");
        }
        if (decision.getConfidence() == null || decision.getConfidence() < properties.getMinConfidence()) {
            errors.add("模型置信度不足，最低要求=" + properties.getMinConfidence());
        }

        String toolName = normalize(decision.getToolName());
        AgentReadToolSpec selectedSpec = findSpec(toolName, enabledSpecs);
        if (toolName.isBlank()) {
            errors.add("toolName 不能为空");
        } else if (selectedSpec == null) {
            errors.add("工具不存在或未启用：" + decision.getToolName());
        }

        Map<String, Object> arguments = decision.getArguments() == null ? Map.of() : decision.getArguments();
        AgentActionParameterValidationResult parameterResult = selectedSpec == null
                ? new AgentActionParameterValidationResult(Map.of(), List.of(), List.of())
                : schemaValidator.validate(selectedSpec.getParameters(), selectedSpec.getRequiredAnyOf(), arguments);
        for (AgentActionValidationIssue issue : parameterResult.issues()) {
            errors.add(issue.field() + ": " + issue.code() + " - " + issue.message());
        }
        if (!parameterResult.missingRequiredFields().isEmpty()) {
            errors.add("缺少必填参数：" + parameterResult.missingRequiredFields());
        }
        if (!errors.isEmpty()) {
            return new LlmReadToolValidationResult(false, false, toolName,
                    parameterResult.parameters(), List.copyOf(errors));
        }
        Map<String, Object> normalizedArguments = new java.util.LinkedHashMap<>(parameterResult.parameters());
        normalizedArguments.putIfAbsent("maxItems", 10);
        return new LlmReadToolValidationResult(true, false, toolName,
                normalizedArguments, List.of());
    }

    private AgentReadToolSpec findSpec(String toolName, List<AgentReadToolSpec> enabledSpecs) {
        if (toolName.isBlank() || enabledSpecs == null) {
            return null;
        }
        for (AgentReadToolSpec spec : enabledSpecs) {
            if (toolName.equals(normalize(spec.getToolName()))) {
                return spec;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
