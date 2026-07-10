package com.cupk.aiacademy.agent.tool.query;

import com.cupk.aiacademy.agent.query.NaturalLanguageQueryService;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;

import java.util.Map;
import java.util.Set;

@Deprecated(forRemoval = true)
public class NaturalLanguageQueryTool implements AgentTool {
    private final NaturalLanguageQueryService queryService;

    public NaturalLanguageQueryTool(NaturalLanguageQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public String name() {
        return "natural_language_query";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("smart_query", "nl_query", "semantic_query", "entity_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        return AgentToolResult.read(name(), queryService.answer(context.normalizedQuery(), context.maxItems()), Map.of());
    }
}
