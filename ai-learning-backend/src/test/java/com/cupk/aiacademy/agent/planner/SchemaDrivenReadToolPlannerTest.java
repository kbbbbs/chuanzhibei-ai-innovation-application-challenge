package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionSchemaValidator;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaDrivenReadToolPlannerTest {
    @Test
    void repairsInvalidToolSelectionThenExecutesOneEnabledReadTool() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentReadToolCatalog catalog = mock(AgentReadToolCatalog.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentPlannerProperties properties = properties();
        AgentReadToolSpec courseSpec = new AgentReadToolSpec("course_search", "查询课程");

        when(catalog.enabledSpecs(List.of("course_search"))).thenReturn(List.of(courseSpec));
        when(model.call(anyString(), anyInt()))
                .thenReturn("""
                        {"decision":"TOOL","toolName":"not_exists","arguments":{"query":"Java 入门"},"confidence":0.9}
                        """)
                .thenReturn("""
                        {"decision":"TOOL","toolName":"course_search","arguments":{"query":"Java 入门","unknown":true},"confidence":0.95}
                        """)
                .thenReturn("""
                        {"decision":"TOOL","toolName":"course_search","arguments":{"query":"Java 入门","maxItems":10},"confidence":0.95}
                        """);
        Map<String, Object> expectedArguments = Map.of("query", "Java 入门", "maxItems", 10);
        when(executor.executeReadTool("course_search", expectedArguments))
                .thenReturn(Optional.of(AgentToolResult.read("course_search", "名称=Java 入门", Map.of())));

        SchemaDrivenReadToolPlanner planner = new SchemaDrivenReadToolPlanner(
                model,
                objectMapper,
                catalog,
                new LlmReadToolDecisionParser(objectMapper),
                new LlmReadToolDecisionValidator(properties, new AgentActionSchemaValidator()),
                executor,
                properties
        );

        String context = planner.planToolContext(agent("[\"course_search\"]"), "现在有没有 Java 入门这门课");

        assertEquals("[course_search]\n名称=Java 入门", context);
        verify(model, org.mockito.Mockito.times(3)).call(anyString(), anyInt());
        verify(executor).executeReadTool("course_search", expectedArguments);
    }

    @Test
    void chatDecisionDoesNotExecuteReadTools() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentReadToolCatalog catalog = mock(AgentReadToolCatalog.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentPlannerProperties properties = properties();
        when(catalog.enabledSpecs(List.of("course_search")))
                .thenReturn(List.of(new AgentReadToolSpec("course_search", "查询课程")));
        when(model.call(anyString(), anyInt())).thenReturn(
                "{\"decision\":\"CHAT\",\"toolName\":\"\",\"arguments\":{},\"confidence\":0.95}"
        );

        SchemaDrivenReadToolPlanner planner = new SchemaDrivenReadToolPlanner(
                model,
                objectMapper,
                catalog,
                new LlmReadToolDecisionParser(objectMapper),
                new LlmReadToolDecisionValidator(properties, new AgentActionSchemaValidator()),
                executor,
                properties
        );

        assertTrue(planner.planToolContext(agent("[\"course_search\"]"), "解释一下递归").isEmpty());
        verify(model).call(anyString(), anyInt());
        verify(executor, never()).executeReadTool(any(), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void selectedReadToolCanVetoFalsePositiveIntent() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentReadToolCatalog catalog = mock(AgentReadToolCatalog.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentPlannerProperties properties = properties();
        AgentReadToolSpec importSpec = new AgentReadToolSpec("import_log_search", "查询导入记录",
                List.of(
                        new com.cupk.aiacademy.agent.action.AgentActionParameterSpec(
                                "httpStatus", "HTTP状态", false, "HTTP状态码",
                                com.cupk.aiacademy.agent.action.AgentActionParameterType.INTEGER,
                                List.of(), List.of(), List.of()),
                        new com.cupk.aiacademy.agent.action.AgentActionParameterSpec(
                                "maxItems", "最大条数", false, "最大条数",
                                com.cupk.aiacademy.agent.action.AgentActionParameterType.INTEGER,
                                List.of(), List.of(), List.of())
                ), List.of());
        when(catalog.enabledSpecs(List.of("import_log_search"))).thenReturn(List.of(importSpec));
        when(model.call(anyString(), anyInt()))
                .thenReturn("{\"decision\":\"CHAT\",\"arguments\":{},\"confidence\":0.9}");

        SchemaDrivenReadToolPlanner planner = new SchemaDrivenReadToolPlanner(
                model, objectMapper, catalog, new LlmReadToolDecisionParser(objectMapper),
                new LlmReadToolDecisionValidator(properties, new AgentActionSchemaValidator()),
                executor, properties);

        assertTrue(
                planner.planSelectedToolContext(
                        agent("[\"import_log_search\"]"),
                        "解释一下 HTTP 400",
                        "import_log_search").isEmpty());
        verify(model).call(anyString(), anyInt());
        verify(executor, never()).executeReadTool(any(), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void exhaustedValidationReturnsNoQueryObservationInsteadOfEmptyContext() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentReadToolCatalog catalog = mock(AgentReadToolCatalog.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentPlannerProperties properties = properties();
        when(catalog.enabledSpecs(List.of("course_search")))
                .thenReturn(List.of(new AgentReadToolSpec("course_search", "查询课程")));
        when(model.call(anyString(), anyInt())).thenReturn(
                "{\"decision\":\"TOOL\",\"toolName\":\"missing_tool\",\"arguments\":{},\"confidence\":0.9}"
        );

        SchemaDrivenReadToolPlanner planner = new SchemaDrivenReadToolPlanner(
                model, objectMapper, catalog, new LlmReadToolDecisionParser(objectMapper),
                new LlmReadToolDecisionValidator(properties, new AgentActionSchemaValidator()),
                executor, properties);

        String context = planner.planToolContext(agent("[\"course_search\"]"), "查一下课程");

        assertTrue(context.contains("未执行平台数据库查询"));
        assertTrue(context.contains("工具不存在或未启用"));
        verify(executor, never()).executeReadTool(any(), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    private AgentPlannerProperties properties() {
        AgentPlannerProperties properties = new AgentPlannerProperties();
        properties.setEnabled(true);
        properties.setMaxRepairRounds(3);
        properties.setMinConfidence(0.6);
        return properties;
    }

    private AgentDefinition agent(String enabledTools) {
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools(enabledTools);
        return agent;
    }

}
