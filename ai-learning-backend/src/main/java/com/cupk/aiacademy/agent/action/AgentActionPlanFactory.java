package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentActionPlanFactory {
    private final AgentPendingActionStore pendingActionStore;
    private final AgentParameterExtractor parameterExtractor;

    @Autowired
    public AgentActionPlanFactory(AgentPendingActionStore pendingActionStore,
                                  AgentParameterExtractor parameterExtractor) {
        this.pendingActionStore = pendingActionStore;
        this.parameterExtractor = parameterExtractor == null
                ? new AgentParameterExtractor(new ObjectMapper())
                : parameterExtractor;
    }

    public AgentActionPlanFactory(AgentPendingActionStore pendingActionStore) {
        this(pendingActionStore, new AgentParameterExtractor(new ObjectMapper()));
    }

    public AgentToolResult createWritePlan(String toolName, AgentActionSpec spec, AgentToolContext context) {
        String rawRequest = context == null ? "" : context.normalizedQuery();
        AgentParameterExtractionResult extraction = parameterExtractor.extract(spec, rawRequest);
        AgentActionPlan plan = new AgentActionPlan(
                spec.actionCode(),
                spec.actionName(),
                spec.domain(),
                spec.riskLevel(),
                spec.requiresConfirmation(),
                spec.confirmationText(),
                spec.summary(),
                planParameters(spec, rawRequest, extraction),
                spec.impacts()
        );

        Map<String, Object> data = new LinkedHashMap<>(plan.toData());
        data.putAll(spec.toData());
        data.put("missingRequiredFields", extraction.missingRequiredFields());
        data.put("parameterExtraction", extraction.toMetadata());

        String content = enrichPromptContent(plan.toPromptContent(), spec, extraction);
        Integer currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null) {
            AgentPendingAction pendingAction = pendingActionStore.create(
                    spec.actionCode(),
                    currentUserId,
                    pendingParameters(spec, rawRequest, extraction),
                    spec.pendingTtl()
            );
            data.put("actionId", pendingAction.actionId());
            data.put("createdBy", pendingAction.createdBy());
            data.put("createdAt", pendingAction.createdAt().toString());
            data.put("expiresAt", pendingAction.expiresAt().toString());
            content = content + "；确认ID=" + pendingAction.actionId()
                    + "；过期时间=" + pendingAction.expiresAt();
        }

        return AgentToolResult.writePlan(toolName, content, data);
    }

    private Map<String, Object> planParameters(AgentActionSpec spec,
                                               String rawRequest,
                                               AgentParameterExtractionResult extraction) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", rawRequest);
        parameters.putAll(extraction.parameters());
        parameters.put("requiredFields", spec.requiredFieldNames());
        parameters.put("requiredConstraints", spec.requiredConstraintLabels());
        parameters.put("requiredAnyOf", spec.requiredAnyOf());
        parameters.put("optionalFields", spec.optionalFieldNames());
        parameters.put("missingRequiredFields", extraction.missingRequiredFields());
        parameters.put("parameterSchema", spec.parameterSchema());
        return parameters;
    }

    private Map<String, Object> pendingParameters(AgentActionSpec spec,
                                                  String rawRequest,
                                                  AgentParameterExtractionResult extraction) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", rawRequest);
        parameters.putAll(extraction.parameters());
        parameters.put("requiredFields", spec.requiredFieldNames());
        parameters.put("requiredConstraints", spec.requiredConstraintLabels());
        parameters.put("requiredAnyOf", spec.requiredAnyOf());
        parameters.put("optionalFields", spec.optionalFieldNames());
        parameters.put("missingRequiredFields", extraction.missingRequiredFields());
        parameters.put("parameterSchema", spec.parameterSchema());
        return parameters;
    }

    private String enrichPromptContent(String content,
                                       AgentActionSpec spec,
                                       AgentParameterExtractionResult extraction) {
        StringBuilder builder = new StringBuilder(content == null ? "" : content);
        appendRecognizedParameterHints(builder, spec, extraction.parameters());
        appendMissingParameterHints(builder, spec, extraction.missingRequiredFields());
        appendParameterHints(builder, "可选字段", spec.parameters().stream()
                .filter(parameter -> !parameter.required())
                .toList());
        return builder.toString();
    }

    private void appendMissingParameterHints(StringBuilder builder,
                                             AgentActionSpec spec,
                                             List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return;
        }
        List<String> labels = new java.util.ArrayList<>();
        for (String missingField : missingFields) {
            AgentActionParameterSpec parameter = spec.parameter(missingField);
            labels.add(parameter == null ? missingField : parameter.displayName());
        }
        builder.append("；待补齐字段=").append(String.join("、", labels));
    }

    private void appendRecognizedParameterHints(StringBuilder builder,
                                                AgentActionSpec spec,
                                                Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        List<AgentActionParameterSpec> recognized = spec.parameters().stream()
                .filter(parameter -> parameters.containsKey(parameter.name()))
                .toList();
        if (recognized.isEmpty()) {
            return;
        }
        builder.append("；已识别参数=");
        for (int i = 0; i < recognized.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            AgentActionParameterSpec parameter = recognized.get(i);
            builder.append(parameter.displayName()).append("=").append(parameters.get(parameter.name()));
        }
    }

    private void appendParameterHints(StringBuilder builder,
                                      String title,
                                      List<AgentActionParameterSpec> parameters) {
        if (parameters.isEmpty()) {
            return;
        }
        builder.append("；").append(title).append("=");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(parameters.get(i).displayName());
        }
    }
}
