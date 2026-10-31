package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionParameterSpec;
import com.cupk.aiacademy.agent.action.AgentActionParameterType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentReadToolSpec {
    private String toolName;
    private String description;
    private List<AgentActionParameterSpec> parameters;
    private List<List<String>> requiredAnyOf;

    public AgentReadToolSpec(String toolName, String description) {
        this(toolName, description, List.of(
                new AgentActionParameterSpec("query", "查询内容", true,
                        "根据用户原意整理后的查询文本，不得补造实体或ID"),
                new AgentActionParameterSpec("maxItems", "最大返回条数", false,
                        "1 到 50，默认 10", AgentActionParameterType.INTEGER,
                        List.of(), List.of(), List.of("10"), null,
                        BigDecimal.ONE, BigDecimal.valueOf(50), "")
        ), List.of());
    }

    public AgentReadToolSpec(String toolName,
                             String description,
                             List<AgentActionParameterSpec> parameters,
                             List<List<String>> requiredAnyOf) {
        this.toolName = toolName == null ? "" : toolName.trim();
        this.description = description == null ? "" : description.trim();
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        List<List<String>> groups = new ArrayList<>();
        if (requiredAnyOf != null) {
            for (List<String> group : requiredAnyOf) {
                groups.add(group == null ? List.of() : List.copyOf(group));
            }
        }
        this.requiredAnyOf = Collections.unmodifiableList(groups);
    }

    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", toolName);
        data.put("description", description);
        data.put("arguments", parameters.stream().map(AgentActionParameterSpec::toData).toList());
        if (!requiredAnyOf.isEmpty()) {
            data.put("requiredAnyOf", requiredAnyOf);
        }
        data.put("additionalProperties", false);
        return data;
    }
}
