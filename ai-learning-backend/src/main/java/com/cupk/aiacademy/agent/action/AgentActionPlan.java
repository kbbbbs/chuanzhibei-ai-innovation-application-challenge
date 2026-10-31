package com.cupk.aiacademy.agent.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentActionPlan(
        String actionCode,
        String actionName,
        String domain,
        AgentActionRiskLevel riskLevel,
        boolean requiresConfirmation,
        String confirmationText,
        String summary,
        Map<String, Object> parameters,
        List<String> impacts
) {
    public AgentActionPlan(String actionCode,
                           String actionName,
                           String domain,
                           AgentActionRiskLevel riskLevel,
                           boolean requiresConfirmation,
                           String summary,
                           Map<String, Object> parameters,
                           List<String> impacts) {
        this(actionCode, actionName, domain, riskLevel, requiresConfirmation, null, summary, parameters, impacts);
    }

    public AgentActionPlan {
        actionCode = blankToDefault(actionCode, "unknown_action");
        actionName = blankToDefault(actionName, actionCode);
        domain = blankToDefault(domain, "general");
        riskLevel = riskLevel == null ? AgentActionRiskLevel.MEDIUM : riskLevel;
        confirmationText = confirmationText == null ? "" : confirmationText.trim();
        summary = summary == null ? "" : summary;
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        impacts = impacts == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(impacts));
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
        data.put("parameters", parameters);
        data.put("impacts", impacts);
        return data;
    }

    public String toPromptContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("操作计划=").append(actionName)
                .append("；领域=").append(domain)
                .append("；风险等级=").append(riskLevel)
                .append("；需要确认=").append(requiresConfirmation ? "是" : "否");
        if (!confirmationText.isBlank()) {
            builder.append("；二次确认文本=").append(confirmationText);
        }
        if (!summary.isBlank()) {
            builder.append("；摘要=").append(summary);
        }
        if (!parameters.isEmpty()) {
            builder.append("；参数=").append(parameters);
        }
        if (!impacts.isEmpty()) {
            builder.append("；影响=").append(String.join("、", impacts));
        }
        return builder.toString();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
