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
import java.util.Optional;

@Service
public class AgentToolExecutor {
    private static final java.util.Set<String> COURSE_SCOPED_READ_TOOLS = java.util.Set.of(
            "course_search", "course_category_search", "course_by_category_search", "chapter_search",
            "lesson_search", "course_structure_search", "course_resource_search", "teaching_class_search",
            "homework_search", "learning_progress", "learning_plan", "course_stats", "learning_analysis_report");
    private final Map<String, AgentTool> toolsByName;

    public AgentToolExecutor(List<AgentTool> tools) {
        this.toolsByName = registerTools(tools);
    }

    @Transactional(readOnly = true)
    public String buildToolContext(List<String> enabledTools, String query, int maxItems, int maxCharacters) {
        if (maxCharacters <= 0) {
            return "";
        }

        List<AgentTool> tools = resolveTools(enabledTools, true);
        if (tools.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (AgentToolResult result : executeResolvedTools(tools, query, maxItems)) {
            appendToolSection(context, result);
            if (context.length() >= maxCharacters) {
                break;
            }
        }
        return limit(context.toString(), maxCharacters);
    }

    @Transactional
    public List<AgentToolResult> executeTools(List<String> enabledTools, String query, int maxItems) {
        if (enabledTools == null || enabledTools.isEmpty() || maxItems <= 0) {
            return List.of();
        }

        List<AgentTool> tools = resolveTools(enabledTools, false);
        if (tools.isEmpty()) {
            return List.of();
        }

        return executeResolvedTools(tools, query, maxItems);
    }

    @Transactional(readOnly = true)
    public Optional<AgentToolResult> executeReadTool(String toolName, String query, int maxItems) {
        return executeReadTool(toolName, Map.of("query", query, "maxItems", maxItems));
    }

    @Transactional(readOnly = true)
    public Optional<AgentToolResult> executeReadTool(String toolName, Map<String, Object> arguments) {
        AgentTool tool = toolsByName.get(normalizeName(toolName));
        if (tool == null || tool.declaredOperationType() != com.cupk.aiacademy.agent.tool.api.AgentToolOperationType.READ) {
            return Optional.empty();
        }
        if (com.cupk.aiacademy.agent.tool.support.AgentToolScope.courseId() != null
                && !COURSE_SCOPED_READ_TOOLS.contains(tool.name())) {
            return Optional.of(AgentToolResult.read(tool.name(), "此查询需要在通用对话中发起，当前对话固定在本课程。", Map.of()));
        }
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        String query = textArgument(safeArguments, "query");
        int maxItems = intArgument(safeArguments, "maxItems", 10);
        AgentToolResult result = executeObserved(tool, new AgentToolContext(
                query,
                Math.max(1, Math.min(50, maxItems)),
                safeArguments
        ));
        return result == null ? Optional.empty() : Optional.of(result.withToolName(tool.name()));
    }

    private AgentToolResult executeObserved(AgentTool tool, AgentToolContext context) {
        com.cupk.aiacademy.agent.kernel.AgentExecutionProgress.emit("TOOL_VALIDATING", tool.name());
        com.cupk.aiacademy.agent.kernel.AgentExecutionProgress.emit("TOOL_RUNNING", tool.name());
        try {
            var result = tool.execute(context);
            com.cupk.aiacademy.agent.kernel.AgentExecutionProgress.emit(
                    result != null && result.success() ? "TOOL_SUCCEEDED" : "TOOL_FAILED", tool.name(),
                    result != null && result.success() ? com.cupk.aiacademy.agent.kernel.AgentExecutionProgress.summary(result.data()) : null);
            return result;
        } catch (RuntimeException failure) {
            com.cupk.aiacademy.agent.kernel.AgentExecutionProgress.emit("TOOL_FAILED", tool.name());
            throw failure;
        }
    }

    private String textArgument(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intArgument(Map<String, Object> arguments, String name, int fallback) {
        Object value = arguments.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<AgentToolResult> executeResolvedTools(List<AgentTool> tools, String query, int maxItems) {
        List<AgentToolResult> results = new ArrayList<>();
        AgentToolContext toolContext = new AgentToolContext(query, maxItems);
        for (AgentTool tool : tools) {
            if (com.cupk.aiacademy.agent.tool.support.AgentToolScope.courseId() != null
                    && tool.declaredOperationType() == com.cupk.aiacademy.agent.tool.api.AgentToolOperationType.READ
                    && !COURSE_SCOPED_READ_TOOLS.contains(tool.name())) continue;
            AgentToolResult result = executeObserved(tool, toolContext);
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

    private List<AgentTool> resolveTools(List<String> enabledTools, boolean promptContextOnly) {
        LinkedHashSet<AgentTool> resolved = new LinkedHashSet<>();
        for (String enabledTool : enabledTools) {
            AgentTool tool = toolsByName.get(normalizeName(enabledTool));
            if (tool != null && (!promptContextOnly || tool.promptContextEnabled())) {
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
