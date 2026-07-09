package com.cupk.aiacademy.agent.action;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentActionSpec(
        String actionCode,
        String actionName,
        String domain,
        AgentActionRiskLevel riskLevel,
        boolean requiresConfirmation,
        String confirmationText,
        String summary,
        List<AgentActionParameterSpec> parameters,
        List<List<String>> requiredAnyOf,
        List<String> impacts,
        Duration pendingTtl
) {
    public static final Duration DEFAULT_PENDING_TTL = Duration.ofMinutes(10);

    public AgentActionSpec(String actionCode,
                           String actionName,
                           String domain,
                           AgentActionRiskLevel riskLevel,
                           boolean requiresConfirmation,
                           String confirmationText,
                           String summary,
                           List<AgentActionParameterSpec> parameters,
                           List<String> impacts,
                           Duration pendingTtl) {
        this(actionCode, actionName, domain, riskLevel, requiresConfirmation, confirmationText, summary,
                parameters, List.of(), impacts, pendingTtl);
    }

    public AgentActionSpec {
        actionCode = blankToDefault(actionCode, "unknown_action");
        actionName = blankToDefault(actionName, actionCode);
        domain = blankToDefault(domain, "general");
        riskLevel = riskLevel == null ? AgentActionRiskLevel.MEDIUM : riskLevel;
        confirmationText = confirmationText == null ? "" : confirmationText.trim();
        summary = summary == null ? "" : summary.trim();
        parameters = immutableParameters(parameters);
        requiredAnyOf = immutableFieldGroups(requiredAnyOf, parameters);
        impacts = immutableStrings(impacts);
        pendingTtl = pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()
                ? DEFAULT_PENDING_TTL
                : pendingTtl;
    }

    public List<String> requiredFieldNames() {
        return fieldNames(true);
    }

    public List<String> optionalFieldNames() {
        return fieldNames(false);
    }

    public List<String> requiredConstraintLabels() {
        List<String> labels = new ArrayList<>(requiredFieldNames());
        for (List<String> group : requiredAnyOf) {
            labels.add(String.join(" 或 ", group));
        }
        return Collections.unmodifiableList(labels);
    }

    public List<Map<String, Object>> parameterSchema() {
        List<Map<String, Object>> schema = new ArrayList<>();
        for (AgentActionParameterSpec parameter : parameters) {
            schema.add(parameter.toData());
        }
        return Collections.unmodifiableList(schema);
    }

    public Map<String, Object> jsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        for (AgentActionParameterSpec parameter : parameters) {
            properties.put(parameter.name(), parameter.toJsonSchemaProperty());
        }
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", requiredFieldNames());
        appendRequiredAnyOf(schema);
        return Collections.unmodifiableMap(schema);
    }

    public AgentActionParameterSpec parameter(String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }
        String normalized = fieldName.trim();
        for (AgentActionParameterSpec parameter : parameters) {
            if (parameter.name().equals(normalized)) {
                return parameter;
            }
        }
        return null;
    }

    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actionCode", actionCode);
        data.put("actionName", actionName);
        data.put("domain", domain);
        data.put("riskLevel", riskLevel.name());
        data.put("requiresConfirmation", requiresConfirmation);
        if (!confirmationText.isBlank()) {
            data.put("confirmationText", confirmationText);
        }
        data.put("summary", summary);
        data.put("requiredFields", requiredFieldNames());
        data.put("requiredConstraints", requiredConstraintLabels());
        data.put("requiredAnyOf", requiredAnyOf);
        data.put("optionalFields", optionalFieldNames());
        data.put("parameterSchema", parameterSchema());
        data.put("jsonSchema", jsonSchema());
        data.put("impacts", impacts);
        data.put("pendingTtlSeconds", pendingTtl.toSeconds());
        return data;
    }

    private List<String> fieldNames(boolean required) {
        List<String> names = new ArrayList<>();
        for (AgentActionParameterSpec parameter : parameters) {
            if (parameter.required() == required) {
                names.add(parameter.name());
            }
        }
        return Collections.unmodifiableList(names);
    }

    private void appendRequiredAnyOf(Map<String, Object> schema) {
        if (requiredAnyOf.isEmpty()) {
            return;
        }
        List<Map<String, Object>> anyOfGroups = requiredAnyOf.stream()
                .map(this::jsonSchemaAnyOfGroup)
                .toList();
        if (anyOfGroups.size() == 1) {
            schema.put("anyOf", anyOfGroups.get(0).get("anyOf"));
        } else {
            schema.put("allOf", anyOfGroups);
        }
    }

    private Map<String, Object> jsonSchemaAnyOfGroup(List<String> group) {
        List<Map<String, Object>> alternatives = new ArrayList<>();
        for (String fieldName : group) {
            alternatives.add(Map.of("required", List.of(fieldName)));
        }
        return Map.of("anyOf", Collections.unmodifiableList(alternatives));
    }

    private static List<AgentActionParameterSpec> immutableParameters(List<AgentActionParameterSpec> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(parameters.stream()
                .filter(Objects::nonNull)
                .toList());
    }

    private static List<List<String>> immutableFieldGroups(List<List<String>> groups,
                                                           List<AgentActionParameterSpec> parameters) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        List<String> parameterNames = parameters == null
                ? List.of()
                : parameters.stream().filter(Objects::nonNull).map(AgentActionParameterSpec::name).toList();
        List<List<String>> copy = new ArrayList<>();
        for (List<String> group : groups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            List<String> fieldNames = new ArrayList<>();
            for (String fieldName : group) {
                if (fieldName != null && !fieldName.trim().isEmpty()
                        && parameterNames.contains(fieldName.trim())) {
                    fieldNames.add(fieldName.trim());
                }
            }
            if (fieldNames.size() > 1) {
                copy.add(Collections.unmodifiableList(fieldNames));
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<String> immutableStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                copy.add(value.trim());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
