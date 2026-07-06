package com.cupk.aiacademy.agent.tool;

import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentToolExecutor {
    private final Map<String, AgentTool> toolsByName;

    public AgentToolExecutor(List<AgentTool> tools) {
        this.toolsByName = registerTools(tools);
    }

    @Transactional(readOnly = true)
    public String buildToolContext(List<String> enabledTools, String query, int maxItems, int maxCharacters) {
        if (maxCharacters <= 0) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (AgentToolResult result : executeTools(enabledTools, query, maxItems)) {
            appendToolSection(context, result);
            if (context.length() >= maxCharacters) {
                break;
            }
        }
        return limit(context.toString(), maxCharacters);
    }

    @Transactional(readOnly = true)
    public List<AgentToolResult> executeTools(List<String> enabledTools, String query, int maxItems) {
        if (enabledTools == null || enabledTools.isEmpty() || maxItems <= 0) {
            return List.of();
        }

        List<AgentTool> tools = resolveTools(enabledTools);
        if (tools.isEmpty()) {
            return List.of();
        }

        List<AgentToolResult> results = new ArrayList<>();
        AgentToolContext toolContext = new AgentToolContext(query, maxItems);
        for (AgentTool tool : tools) {
            AgentToolResult result = tool.execute(toolContext);
            if (result != null) {
                results.add(result.withToolName(tool.name()));
            }
        }
        return results;
    }

    private Map<String, AgentTool> registerTools(List<AgentTool> tools) {
        Map<String, AgentTool> registeredTools = new LinkedHashMap<>();
        if (tools == null) {
            return registeredTools;
        }
        for (AgentTool tool : tools) {
            registerToolName(registeredTools, tool.name(), tool);
            for (String alias : tool.aliases()) {
                registerToolName(registeredTools, alias, tool);
            }
        }
        return registeredTools;
    }

    private void registerToolName(Map<String, AgentTool> registeredTools, String toolName, AgentTool tool) {
        String normalized = normalizeName(toolName);
        if (!normalized.isEmpty()) {
            registeredTools.putIfAbsent(normalized, tool);
        }
    }

    private List<AgentTool> resolveTools(List<String> enabledTools) {
        LinkedHashSet<AgentTool> resolved = new LinkedHashSet<>();
        for (String enabledTool : enabledTools) {
            AgentTool tool = toolsByName.get(normalizeName(enabledTool));
            if (tool != null) {
                resolved.add(tool);
            }
        }
        return new ArrayList<>(resolved);
    }

    private void appendToolSection(StringBuilder context, AgentToolResult result) {
        if (result == null || !result.hasContent()) {
            return;
        }
        if (!context.isEmpty()) {
            context.append("\n");
        }
        context.append("[").append(result.toolName()).append("]\n").append(result.content());
    }

    private String normalizeName(String toolName) {
        if (isBlank(toolName)) {
            return "";
        }
        return toolName.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
