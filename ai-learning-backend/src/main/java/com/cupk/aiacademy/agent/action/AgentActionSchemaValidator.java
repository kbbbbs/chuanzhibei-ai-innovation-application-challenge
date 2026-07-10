package com.cupk.aiacademy.agent.action;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AgentActionSchemaValidator {
    public AgentActionParameterValidationResult validate(AgentActionSpec spec, Map<String, Object> rawParameters) {
        if (spec == null) {
            return new AgentActionParameterValidationResult(Map.of(), List.of(), List.of());
        }
        return validate(spec.parameters(), spec.requiredAnyOf(), rawParameters);
    }

    public AgentActionParameterValidationResult validate(List<AgentActionParameterSpec> parameters,
                                                         List<List<String>> requiredAnyOf,
                                                         Map<String, Object> rawParameters) {
        List<AgentActionParameterSpec> safeParameters = parameters == null ? List.of() : parameters;
        List<List<String>> safeRequiredAnyOf = requiredAnyOf == null ? List.of() : requiredAnyOf;

        Map<String, Object> normalized = new LinkedHashMap<>();
        List<AgentActionValidationIssue> issues = new ArrayList<>();
        Map<String, AgentActionParameterSpec> parametersByAcceptedName = parametersByAcceptedName(safeParameters);

        if (rawParameters != null) {
            for (Map.Entry<String, Object> entry : rawParameters.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                AgentActionParameterSpec parameter = parametersByAcceptedName.get(normalizeName(entry.getKey()));
                if (parameter == null) {
                    issues.add(new AgentActionValidationIssue(entry.getKey(), "unknown_field", "字段不在动作 schema 中"));
                    continue;
                }
                Object converted = convert(parameter, entry.getValue(), issues);
                if (missingValue(converted)) {
                    continue;
                }
                if (!matchesEnum(parameter, converted)) {
                    issues.add(new AgentActionValidationIssue(parameter.name(), "enum_mismatch",
                            "字段值不在允许范围：" + parameter.enumValues()));
                    continue;
                }
                if (!matchesConstraints(parameter, converted, issues)) {
                    continue;
                }
                normalized.put(parameter.name(), converted);
            }
        }

        List<String> missing = missingRequiredFields(safeParameters, safeRequiredAnyOf, normalized);
        return new AgentActionParameterValidationResult(normalized, missing, issues);
    }

    private Map<String, AgentActionParameterSpec> parametersByAcceptedName(List<AgentActionParameterSpec> parameters) {
        Map<String, AgentActionParameterSpec> byName = new LinkedHashMap<>();
        for (AgentActionParameterSpec parameter : parameters) {
            for (String acceptedName : parameter.allNamesForExtraction()) {
                String normalized = normalizeName(acceptedName);
                if (!normalized.isEmpty()) {
                    byName.putIfAbsent(normalized, parameter);
                }
            }
        }
        return byName;
    }

    private Object convert(AgentActionParameterSpec parameter,
                           Object value,
                           List<AgentActionValidationIssue> issues) {
        if (missingValue(value)) {
            return null;
        }
        return switch (parameter.type()) {
            case INTEGER -> integerValue(parameter, value, issues);
            case NUMBER -> decimalValue(parameter, value, issues);
            case BOOLEAN -> booleanValue(parameter, value, issues);
            case ARRAY -> value instanceof List<?> ? value : invalid(parameter, "type_mismatch", "字段必须是数组", issues);
            case OBJECT -> value instanceof Map<?, ?> ? value : invalid(parameter, "type_mismatch", "字段必须是对象", issues);
            case STRING -> stringValue(value);
        };
    }

    private Integer integerValue(AgentActionParameterSpec parameter,
                                 Object value,
                                 List<AgentActionValidationIssue> issues) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            invalid(parameter, "type_mismatch", "字段必须是整数", issues);
            return null;
        }
    }

    private BigDecimal decimalValue(AgentActionParameterSpec parameter,
                                    Object value,
                                    List<AgentActionValidationIssue> issues) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            invalid(parameter, "type_mismatch", "字段必须是数字", issues);
            return null;
        }
    }

    private Boolean booleanValue(AgentActionParameterSpec parameter,
                                 Object value,
                                 List<AgentActionValidationIssue> issues) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "y", "是", "开启", "启用").contains(text)) {
            return true;
        }
        if (List.of("false", "0", "no", "n", "否", "关闭", "停用").contains(text)) {
            return false;
        }
        invalid(parameter, "type_mismatch", "字段必须是布尔值", issues);
        return null;
    }

    private String stringValue(Object value) {
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Object invalid(AgentActionParameterSpec parameter,
                           String code,
                           String message,
                           List<AgentActionValidationIssue> issues) {
        issues.add(new AgentActionValidationIssue(parameter.name(), code, message));
        return null;
    }

    private boolean matchesEnum(AgentActionParameterSpec parameter, Object value) {
        if (parameter.enumValues().isEmpty()) {
            return true;
        }
        String text = String.valueOf(value).trim();
        for (String enumValue : parameter.enumValues()) {
            if (enumValue.equals(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesConstraints(AgentActionParameterSpec parameter,
                                       Object value,
                                       List<AgentActionValidationIssue> issues) {
        if (value == null) {
            return true;
        }
        if (parameter.type() == AgentActionParameterType.STRING) {
            String text = String.valueOf(value);
            if (parameter.maxLength() != null && text.length() > parameter.maxLength()) {
                issues.add(new AgentActionValidationIssue(parameter.name(), "max_length_exceeded",
                        "字段长度不能超过 " + parameter.maxLength()));
                return false;
            }
            if (!parameter.pattern().isBlank() && !text.matches(parameter.pattern())) {
                issues.add(new AgentActionValidationIssue(parameter.name(), "pattern_mismatch",
                        "字段格式不符合要求"));
                return false;
            }
        }
        if (parameter.type() == AgentActionParameterType.INTEGER || parameter.type() == AgentActionParameterType.NUMBER) {
            BigDecimal number = decimalValueForConstraint(value);
            if (number != null && parameter.minimum() != null && number.compareTo(parameter.minimum()) < 0) {
                issues.add(new AgentActionValidationIssue(parameter.name(), "below_minimum",
                        "字段不能小于 " + parameter.minimum()));
                return false;
            }
            if (number != null && parameter.maximum() != null && number.compareTo(parameter.maximum()) > 0) {
                issues.add(new AgentActionValidationIssue(parameter.name(), "above_maximum",
                        "字段不能大于 " + parameter.maximum()));
                return false;
            }
        }
        return true;
    }

    private BigDecimal decimalValueForConstraint(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> missingRequiredFields(List<AgentActionParameterSpec> parameterSpecs,
                                               List<List<String>> requiredAnyOf,
                                               Map<String, Object> parameters) {
        if (parameterSpecs.isEmpty() && requiredAnyOf.isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (AgentActionParameterSpec parameter : parameterSpecs) {
            if (parameter.required() && missingValue(parameters.get(parameter.name()))) {
                missing.add(parameter.name());
            }
        }
        for (List<String> group : requiredAnyOf) {
            boolean anyPresent = group.stream().anyMatch(fieldName -> !missingValue(parameters.get(fieldName)));
            if (!anyPresent) {
                missing.add(String.join(" 或 ", group));
            }
        }
        return missing;
    }

    private boolean missingValue(Object value) {
        return value == null
                || (value instanceof String text && text.isBlank())
                || (value instanceof List<?> list && list.isEmpty())
                || (value instanceof Map<?, ?> map && map.isEmpty());
    }

    private String normalizeName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }
}
