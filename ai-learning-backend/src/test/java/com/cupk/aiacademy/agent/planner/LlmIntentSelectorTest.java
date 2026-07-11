package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmIntentSelectorTest {
    @Test
    void selectsWriteActionFromCompactCatalog() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentActionCatalog actionCatalog = mock(AgentActionCatalog.class);
        AgentReadToolCatalog readCatalog = mock(AgentReadToolCatalog.class);
        AgentPlannerProperties properties = properties();
        List<String> enabledTools = List.of("homework_create_plan", "course_search");
        when(actionCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of(
                Map.of("code", "homework.create", "summary", "发布作业")
        ));
        when(readCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of(
                Map.of("code", "course_search", "summary", "查询课程")
        ));
        when(model.call(anyString())).thenReturn(
                "{\"decision\":\"WRITE\",\"code\":\"homework.create\",\"confidence\":0.96}");

        LlmIntentSelector selector = new LlmIntentSelector(
                model, new ObjectMapper(), actionCatalog, readCatalog, properties);

        AgentIntentSelection result = selector.select(
                agent("[\"homework_create_plan\",\"course_search\"]"),
                "给 Java 入门布置作业");

        assertEquals(AgentIntentSelection.Type.WRITE, result.getType());
        assertEquals("homework.create", result.getCode());
        verify(model).call(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("homework.create")
                        && prompt.contains("course_search")
                        && !prompt.contains("parameterSchema")));
    }

    @Test
    void repairsUnknownReadToolOnce() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentActionCatalog actionCatalog = mock(AgentActionCatalog.class);
        AgentReadToolCatalog readCatalog = mock(AgentReadToolCatalog.class);
        AgentPlannerProperties properties = properties();
        List<String> enabledTools = List.of("course_search");
        when(actionCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of());
        when(readCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of(
                Map.of("code", "course_search", "summary", "查询课程")
        ));
        when(model.call(anyString()))
                .thenReturn("{\"decision\":\"READ\",\"code\":\"missing\",\"confidence\":0.9}")
                .thenReturn("{\"decision\":\"READ\",\"code\":\"course_search\",\"confidence\":0.95}");

        LlmIntentSelector selector = new LlmIntentSelector(
                model, new ObjectMapper(), actionCatalog, readCatalog, properties);

        AgentIntentSelection result = selector.select(agent("[\"course_search\"]"), "现在有哪些课程");

        assertEquals(AgentIntentSelection.Type.READ, result.getType());
        assertEquals("course_search", result.getCode());
        verify(model, org.mockito.Mockito.times(2)).call(anyString());
    }

    @Test
    void noEnabledToolsSkipsModel() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentActionCatalog actionCatalog = mock(AgentActionCatalog.class);
        AgentReadToolCatalog readCatalog = mock(AgentReadToolCatalog.class);
        AgentPlannerProperties properties = properties();
        when(actionCatalog.enabledIntentSummaries(List.of())).thenReturn(List.of());
        when(readCatalog.enabledIntentSummaries(List.of())).thenReturn(List.of());

        AgentIntentSelection result = new LlmIntentSelector(
                model, new ObjectMapper(), actionCatalog, readCatalog, properties)
                .select(agent("[]"), "解释递归");

        assertEquals(AgentIntentSelection.Type.CHAT, result.getType());
        assertFalse(org.mockito.Mockito.mockingDetails(model).getInvocations().iterator().hasNext());
    }

    @Test
    void retriesEmptyPlannerResponseBeforeSelectingIntent() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentActionCatalog actionCatalog = mock(AgentActionCatalog.class);
        AgentReadToolCatalog readCatalog = mock(AgentReadToolCatalog.class);
        AgentPlannerProperties properties = properties();
        List<String> enabledTools = List.of("course_create_plan");
        when(actionCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of(
                Map.of("code", "course.create", "summary", "新增课程")
        ));
        when(readCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of());
        when(model.call(anyString()))
                .thenThrow(new AgentPlanningException("规划模型返回为空", null,
                        "操作规划服务未返回结果，请稍后重试。"))
                .thenReturn("{\"decision\":\"WRITE\",\"code\":\"course.create\",\"confidence\":0.95}");

        AgentIntentSelection result = new LlmIntentSelector(
                model, new ObjectMapper(), actionCatalog, readCatalog, properties)
                .select(agent("[\"course_create_plan\"]"), "新增一门 Python 入门课");

        assertEquals(AgentIntentSelection.Type.WRITE, result.getType());
        assertEquals("course.create", result.getCode());
        verify(model, times(2)).call(anyString());
    }

    @Test
    void exhaustedIntentValidationExposesSafeUserFeedback() {
        PlannerModelClient model = mock(PlannerModelClient.class);
        AgentActionCatalog actionCatalog = mock(AgentActionCatalog.class);
        AgentReadToolCatalog readCatalog = mock(AgentReadToolCatalog.class);
        AgentPlannerProperties properties = properties();
        List<String> enabledTools = List.of("course_create_plan");
        when(actionCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of(
                Map.of("code", "course.create", "summary", "新增课程")
        ));
        when(readCatalog.enabledIntentSummaries(enabledTools)).thenReturn(List.of());
        when(model.call(anyString())).thenReturn(
                "{\"decision\":\"WRITE\",\"code\":\"missing\",\"confidence\":0.95}"
        );

        AgentPlanningException exception = assertThrows(AgentPlanningException.class, () ->
                new LlmIntentSelector(model, new ObjectMapper(), actionCatalog, readCatalog, properties)
                        .select(agent("[\"course_create_plan\"]"), "新增一门 Python 入门课"));

        assertEquals(2, exception.getAttempts());
        assertTrue(exception.getUserMessage().contains("最大尝试次数（2 次）"));
        assertTrue(exception.getUserMessage().contains("功能当前未启用"));
        verify(model, times(2)).call(anyString());
    }

    private AgentPlannerProperties properties() {
        AgentPlannerProperties properties = new AgentPlannerProperties();
        properties.setEnabled(true);
        properties.setMaxRepairRounds(1);
        properties.setMinConfidence(0.6);
        return properties;
    }

    private AgentDefinition agent(String enabledTools) {
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools(enabledTools);
        return agent;
    }
}
