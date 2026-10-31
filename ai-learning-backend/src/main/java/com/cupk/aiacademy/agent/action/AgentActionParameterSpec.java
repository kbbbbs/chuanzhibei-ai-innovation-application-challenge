package com.cupk.aiacademy.agent.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public record AgentActionParameterSpec(
        String name,
        String label,
        boolean required,
        String description,
        AgentActionParameterType type,
        List<String> enumValues,
        List<String> aliases,
        List<String> examples,
        Integer maxLength,
        BigDecimal minimum,
        BigDecimal maximum,
        String pattern
) {
    public AgentActionParameterSpec(String name, String label, boolean required) {
        this(name, label, required, "");
    }

    public AgentActionParameterSpec(String name, String label, boolean required, String description) {
        this(name, label, required, description, null);
    }

    public AgentActionParameterSpec(String name,
                                    String label,
                                    boolean required,
                                    String description,
                                    AgentActionParameterType type) {
        this(name, label, required, description, type, List.of(), List.of(), List.of());
    }

    public AgentActionParameterSpec(String name,
                                    String label,
                                    boolean required,
                                    String description,
                                    AgentActionParameterType type,
                                    List<String> enumValues,
                                    List<String> aliases,
                                    List<String> examples) {
        this(name, label, required, description, type, enumValues, aliases, examples,
                null, null, null, "");
    }

    public AgentActionParameterSpec {
        name = blankToDefault(name, "unknown");
        label = blankToDefault(label, name);
        description = description == null ? "" : description.trim();
        type = type == null ? inferType(name) : type;
        enumValues = immutableStrings(enumValues);
        aliases = immutableStrings(aliases);
        examples = immutableStrings(examples);
        maxLength = maxLength == null || maxLength <= 0 ? null : maxLength;
        pattern = pattern == null ? "" : pattern.trim();
    }

    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("label", label);
        data.put("required", required);
        data.put("type", type.jsonSchemaType());
        if (!description.isBlank()) {
            data.put("description", description);
        }
        if (!enumValues.isEmpty()) {
            data.put("enumValues", enumValues);
        }
        if (!aliases.isEmpty()) {
            data.put("aliases", aliases);
        }
        if (!examples.isEmpty()) {
            data.put("examples", examples);
        }
        if (maxLength != null) {
            data.put("maxLength", maxLength);
        }
        if (minimum != null) {
            data.put("minimum", minimum);
        }
        if (maximum != null) {
            data.put("maximum", maximum);
        }
        if (!pattern.isBlank()) {
            data.put("pattern", pattern);
        }
        return data;
    }

    public Map<String, Object> toJsonSchemaProperty() {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type.jsonSchemaType());
        property.put("title", label);
        if (!description.isBlank()) {
            property.put("description", description);
        }
        if (!enumValues.isEmpty()) {
            property.put("enum", jsonSchemaEnumValues());
        }
        if (!examples.isEmpty()) {
            property.put("examples", examples);
        }
        if (maxLength != null && type == AgentActionParameterType.STRING) {
            property.put("maxLength", maxLength);
        }
        if (minimum != null && supportsNumericBounds()) {
            property.put("minimum", minimum);
        }
        if (maximum != null && supportsNumericBounds()) {
            property.put("maximum", maximum);
        }
        if (!pattern.isBlank() && type == AgentActionParameterType.STRING) {
            property.put("pattern", pattern);
        }
        return property;
    }

    public String displayName() {
        return label.equals(name) ? name : name + "(" + label + ")";
    }

    public List<String> allNamesForExtraction() {
        List<String> names = new ArrayList<>();
        names.add(name);
        if (!label.equals(name)) {
            names.add(label);
        }
        names.addAll(aliases);
        return Collections.unmodifiableList(names);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
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

    private List<Object> jsonSchemaEnumValues() {
        List<Object> values = new ArrayList<>();
        for (String enumValue : enumValues) {
            values.add(switch (type) {
                case INTEGER -> parseInteger(enumValue);
                case NUMBER -> parseDecimal(enumValue);
                case BOOLEAN -> parseBoolean(enumValue);
                default -> enumValue;
            });
        }
        return Collections.unmodifiableList(values);
    }

    private boolean supportsNumericBounds() {
        return type == AgentActionParameterType.INTEGER || type == AgentActionParameterType.NUMBER;
    }

    private Object parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return value;
        }
    }

    private Object parseDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception ignored) {
            return value;
        }
    }

    private Object parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "是".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "否".equals(value)) {
            return false;
        }
        return value;
    }

    private static AgentActionParameterType inferType(String name) {
        String normalized = name == null ? "" : name.replace("_", "").toLowerCase();
        if (normalized.endsWith("id")
                || normalized.endsWith("count")
                || normalized.endsWith("seconds")
                || normalized.endsWith("sortorder")
                || "status".equals(normalized)) {
            return AgentActionParameterType.INTEGER;
        }
        if (normalized.endsWith("score") || "rating".equals(normalized)) {
            return AgentActionParameterType.NUMBER;
        }
        return AgentActionParameterType.STRING;
    }
}
