package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.tool.api.AgentTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class AgentActionCatalog {
    private final Map<String, AgentActionSpec> specsByActionCode;
    private final Map<String, AgentActionSpec> specsByToolName;

    @Autowired
    public AgentActionCatalog(List<AgentTool> tools, AgentActionSpecRegistry specRegistry) {
        this(tools, Optional.ofNullable(specRegistry));
    }

    public AgentActionCatalog(List<AgentTool> tools) {
        this(tools, Optional.empty());
    }

    private AgentActionCatalog(List<AgentTool> tools, Optional<AgentActionSpecRegistry> specRegistry) {
        Map<String, AgentActionSpec> byActionCode = new LinkedHashMap<>();
        Map<String, AgentActionSpec> byToolName = new LinkedHashMap<>();
        if (tools != null) {
            for (AgentTool tool : tools) {
                Optional<AgentActionSpec> optionalSpec = tool.actionSpec();
                AgentActionSpec spec = optionalSpec
                        .or(() -> specRegistry.flatMap(registry -> registry.findByToolName(tool.name())))
                        .orElse(null);
                if (spec == null) {
                    continue;
                }
                byActionCode.putIfAbsent(normalizeActionCode(spec.actionCode()), spec);
                registerToolName(byToolName, tool.name(), spec);
                for (String alias : tool.aliases()) {
                    registerToolName(byToolName, alias, spec);
                }
            }
        }
        specRegistry.ifPresent(registry -> {
            for (AgentActionSpec spec : registry.allSpecs()) {
                byActionCode.putIfAbsent(normalizeActionCode(spec.actionCode()), spec);
            }
        });
        this.specsByActionCode = Collections.unmodifiableMap(byActionCode);
        this.specsByToolName = Collections.unmodifiableMap(byToolName);
    }

    public List<AgentActionSpec> allSpecs() {
        return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(specsByActionCode.values())));
    }

    public Optional<AgentActionSpec> findByActionCode(String actionCode) {
        return Optional.ofNullable(specsByActionCode.get(normalizeActionCode(actionCode)));
    }

    public Optional<AgentActionSpec> findByToolName(String toolName) {
        return Optional.ofNullable(specsByToolName.get(normalizeToolName(toolName)));
    }

    public List<AgentActionSpec> enabledSpecs(List<String> enabledTools) {
        if (enabledTools == null || enabledTools.isEmpty()) {
            return List.of();
        }
        Set<AgentActionSpec> specs = new LinkedHashSet<>();
        for (String enabledTool : enabledTools) {
            findByToolName(enabledTool).ifPresent(specs::add);
        }
        return Collections.unmodifiableList(new ArrayList<>(specs));
    }

    public List<Map<String, Object>> enabledActionSummaries(List<String> enabledTools) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        Set<String> seenActionCodes = new LinkedHashSet<>();
        if (enabledTools == null || enabledTools.isEmpty()) {
            return List.of();
        }
        for (String enabledTool : enabledTools) {
            Optional<AgentActionSpec> optionalSpec = findByToolName(enabledTool);
            if (optionalSpec.isEmpty()) {
                continue;
            }
            AgentActionSpec spec = optionalSpec.get();
            if (!seenActionCodes.add(normalizeActionCode(spec.actionCode()))) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("toolName", normalizeToolName(enabledTool));
            summary.put("actionCode", spec.actionCode());
            summary.put("actionName", spec.actionName());
            summary.put("domain", spec.domain());
            summary.put("summary", spec.summary());
            summary.put("requiredFields", spec.requiredFieldNames());
            summary.put("requiredConstraints", spec.requiredConstraintLabels());
            summary.put("optionalFields", spec.optionalFieldNames());
            summaries.add(summary);
        }
        return Collections.unmodifiableList(summaries);
    }

    public List<Map<String, Object>> enabledIntentSummaries(List<String> enabledTools) {
        if (enabledTools == null || enabledTools.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        Set<String> seenActionCodes = new LinkedHashSet<>();
        for (String enabledTool : enabledTools) {
            Optional<AgentActionSpec> optionalSpec = findByToolName(enabledTool);
            if (optionalSpec.isEmpty()) {
                continue;
            }
            AgentActionSpec spec = optionalSpec.get();
            if (!seenActionCodes.add(normalizeActionCode(spec.actionCode()))) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("code", spec.actionCode());
            summary.put("summary", spec.summary().isBlank() ? spec.actionName() : spec.summary());
            summaries.add(Collections.unmodifiableMap(summary));
        }
        return Collections.unmodifiableList(summaries);
    }

    public Optional<String> findEnabledToolNameByActionCode(List<String> enabledTools, String actionCode) {
        String normalizedActionCode = normalizeActionCode(actionCode);
        if (enabledTools == null || enabledTools.isEmpty() || normalizedActionCode.isEmpty()) {
            return Optional.empty();
        }
        for (String enabledTool : enabledTools) {
            Optional<AgentActionSpec> optionalSpec = findByToolName(enabledTool);
            if (optionalSpec.isPresent()
                    && normalizeActionCode(optionalSpec.get().actionCode()).equals(normalizedActionCode)) {
                return Optional.of(normalizeToolName(enabledTool));
            }
        }
        return Optional.empty();
    }

    private void registerToolName(Map<String, AgentActionSpec> byToolName,
                                  String toolName,
                                  AgentActionSpec spec) {
        String normalized = normalizeToolName(toolName);
        if (!normalized.isEmpty()) {
            byToolName.putIfAbsent(normalized, spec);
        }
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return "";
        }
        return toolName.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    private String normalizeActionCode(String actionCode) {
        if (actionCode == null || actionCode.trim().isEmpty()) {
            return "";
        }
        return actionCode.trim().toLowerCase(Locale.ROOT);
    }
}
