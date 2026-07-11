package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentActionSchemaValidator;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.action.AgentActionSpecRegistry;
import com.cupk.aiacademy.agent.action.InMemoryAgentPendingActionStore;
import com.cupk.aiacademy.agent.tool.course.CourseCreatePlanTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmActionRepairLoopTest {
    @Test
    void missingUserInputStopsBeforeRepairCall() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentActionCatalog catalog = courseCatalog();
        AgentActionSpec spec = catalog.findByActionCode("course.create").orElseThrow();
        AgentPlannerProperties properties = properties(3);
        when(model.call(anyString(), anyInt())).thenReturn("""
                {"decision":"ACTION","actionCode":"course.create","arguments":{"title":"Python 入门"},"missingRequiredFields":["categoryId 或 categoryName"],"userMessage":"","confidence":0.95}
                """);

        LlmActionPlanResult result = loop(model, objectMapper, catalog, properties)
                .plan("新增一门课，名字叫 Python 入门", List.of(spec));

        assertFalse(result.getValidation().isValid());
        assertEquals(List.of("categoryId 或 categoryName"), result.getValidation().getMissingRequiredFields());
        assertEquals(1, result.getAttempts());
        assertFalse(result.isExhausted());
        verify(model).call(anyString(), anyInt());
    }

    @Test
    void retriesTransientPlannerFailureAndCanStillSucceed() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentActionCatalog catalog = courseCatalog();
        AgentActionSpec spec = catalog.findByActionCode("course.create").orElseThrow();
        AgentPlannerProperties properties = properties(1);
        when(model.call(anyString(), anyInt()))
                .thenThrow(new AgentPlanningException("规划模型返回为空", null,
                        "操作规划服务未返回结果，请稍后重试。"))
                .thenReturn("""
                        {"decision":"ACTION","actionCode":"course.create","arguments":{"title":"Python 入门","categoryName":"计算机"},"missingRequiredFields":[],"userMessage":"","confidence":0.95}
                        """);

        LlmActionPlanResult result = loop(model, objectMapper, catalog, properties)
                .plan("新增一门 Python 入门课，分类是计算机", List.of(spec));

        assertTrue(result.getValidation().isValid());
        assertEquals(2, result.getAttempts());
        assertFalse(result.isExhausted());
        verify(model, times(2)).call(anyString(), anyInt());
    }

    @Test
    void exhaustedRepairKeepsLastValidationAndSafeFailureReason() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentActionCatalog catalog = courseCatalog();
        AgentActionSpec spec = catalog.findByActionCode("course.create").orElseThrow();
        AgentPlannerProperties properties = properties(1);
        when(model.call(anyString(), anyInt()))
                .thenReturn("""
                        {"decision":"ACTION","actionCode":"course.create","arguments":{"title":"Python 入门","categoryName":"计算机","unexpected":"x"},"missingRequiredFields":[],"userMessage":"","confidence":0.95}
                        """)
                .thenThrow(new AgentPlanningException("规划模型返回为空", null,
                        "操作规划服务未返回结果，请稍后重试。"));

        LlmActionPlanResult result = loop(model, objectMapper, catalog, properties)
                .plan("新增一门 Python 入门课，分类是计算机", List.of(spec));

        assertFalse(result.getValidation().isValid());
        assertTrue(result.isExhausted());
        assertEquals(2, result.getAttempts());
        assertTrue(result.getValidation().getErrors().stream().anyMatch(error -> error.contains("unexpected")));
        assertTrue(result.getValidation().getErrors().contains("操作规划服务未返回结果，请稍后重试。"));
    }

    @Test
    void repairsMalformedJsonThenReturnsValidatedAction() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentActionCatalog catalog = courseCatalog();
        AgentActionSpec spec = catalog.findByActionCode("course.create").orElseThrow();
        AgentPlannerProperties properties = properties(3);
        when(model.call(anyString(), anyInt()))
                .thenReturn("这不是 JSON")
                .thenReturn("""
                        {"decision":"ACTION","actionCode":"course.create","arguments":{"title":"Java 入门","categoryName":"编程"},"missingRequiredFields":[],"userMessage":"","confidence":0.95}
                        """);

        LlmActionRepairLoop loop = loop(
                model,
                objectMapper,
                catalog,
                properties
        );

        LlmActionPlanResult result = loop.plan("新增一门 Java 入门课，分类是编程", List.of(spec));

        assertTrue(result.getValidation().isValid());
        assertEquals("course.create", result.getValidation().getActionCode());
        assertEquals("Java 入门", result.getValidation().getNormalizedArguments().get("title"));
        verify(model, times(2)).call(anyString(), anyInt());
    }

    private LlmActionRepairLoop loop(PlannerModelClient model,
                                     ObjectMapper objectMapper,
                                     AgentActionCatalog catalog,
                                     AgentPlannerProperties properties) {
        return new LlmActionRepairLoop(
                model,
                new ActionSchemaPromptBuilder(objectMapper),
                new LlmActionDecisionParser(objectMapper),
                new LlmActionDecisionValidator(catalog, new AgentActionSchemaValidator(), properties),
                properties
        );
    }

    private AgentActionCatalog courseCatalog() {
        InMemoryAgentPendingActionStore pendingStore = new InMemoryAgentPendingActionStore();
        return new AgentActionCatalog(
                List.of(new CourseCreatePlanTool(new AgentActionPlanFactory(pendingStore))),
                new AgentActionSpecRegistry()
        );
    }

    private AgentPlannerProperties properties(int maxRepairRounds) {
        AgentPlannerProperties properties = new AgentPlannerProperties();
        properties.setMaxRepairRounds(maxRepairRounds);
        properties.setMinConfidence(0.6);
        return properties;
    }
}
