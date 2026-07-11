package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionClarificationDraft;
import com.cupk.aiacademy.agent.action.AgentActionParameterSpec;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaDrivenAgentPlannerTest {
    @Mock private AgentPlannerProperties properties;
    @Mock private AgentActionCatalog actionCatalog;
    @Mock private LlmActionRepairLoop repairLoop;
    @Mock private LlmValidatedPlanFactory planFactory;

    @Test
    void createsPendingPlanOnlyAfterValidationSucceeds() {
        AgentActionSpec spec = homeworkSpec();
        AgentDefinition agent = agentWithTools();
        LlmActionValidationResult validation = new LlmActionValidationResult(
                true,
                "homework.create",
                Map.of("courseTitle", "Java 入门", "title", "第一次练习"),
                List.of(),
                List.of()
        );
        when(properties.isEnabled()).thenReturn(true);
        when(actionCatalog.enabledSpecs(List.of("homework_create_plan"))).thenReturn(List.of(spec));
        when(repairLoop.plan("给 Java 入门布置作业，标题叫第一次练习", List.of(spec)))
                .thenReturn(new LlmActionPlanResult(actionDecision(), validation));
        when(planFactory.create("给 Java 入门布置作业，标题叫第一次练习", validation))
                .thenReturn(new AgentDirectActionReply("plan", null));

        SchemaDrivenAgentPlanner planner = newPlanner();
        Optional<AgentDirectActionReply> reply = planner.tryPlan(agent, "给 Java 入门布置作业，标题叫第一次练习");

        assertTrue(reply.isPresent());
        verify(planFactory).create("给 Java 入门布置作业，标题叫第一次练习", validation);
    }

    @Test
    void asksUserForMissingFieldsWithoutCreatingPendingAction() {
        AgentActionSpec spec = homeworkSpec();
        AgentDefinition agent = agentWithTools();
        LlmActionValidationResult validation = new LlmActionValidationResult(
                false,
                "homework.create",
                Map.of("title", "第一次练习"),
                List.of("classId 或 className"),
                List.of("缺少必填参数：[classId 或 className]")
        );
        when(properties.isEnabled()).thenReturn(true);
        when(actionCatalog.enabledSpecs(List.of("homework_create_plan"))).thenReturn(List.of(spec));
        when(repairLoop.plan("给 Java 入门布置作业，标题叫第一次练习", List.of(spec)))
                .thenReturn(new LlmActionPlanResult(actionDecision(), validation));

        SchemaDrivenAgentPlanner planner = newPlanner();
        Optional<AgentDirectActionReply> reply = planner.tryPlan(agent, "给 Java 入门布置作业，标题叫第一次练习");

        assertTrue(reply.isPresent());
        assertNull(reply.orElseThrow().pendingAction());
        assertTrue(reply.orElseThrow().content().contains("classId 或 className"));
        verifyNoInteractions(planFactory);
    }

    @Test
    void returnsClarificationWhenRepairRoundsStillHaveSchemaErrors() {
        AgentActionSpec spec = homeworkSpec();
        AgentDefinition agent = agentWithTools();
        LlmActionValidationResult validation = new LlmActionValidationResult(
                false,
                "homework.create",
                Map.of("title", "第一次练习"),
                List.of("classId 或 className"),
                List.of("extra: unknown_field - 字段不在动作 schema 中")
        );
        LlmActionDecision decision = actionDecision();
        decision.setUserMessage("请补充具体教学班");
        when(properties.isEnabled()).thenReturn(true);
        when(actionCatalog.enabledSpecs(List.of("homework_create_plan"))).thenReturn(List.of(spec));
        when(repairLoop.plan("给 Java 入门布置作业，标题叫第一次练习", List.of(spec)))
                .thenReturn(new LlmActionPlanResult(decision, validation));

        Optional<AgentDirectActionReply> reply = newPlanner().tryPlan(
                agent,
                "给 Java 入门布置作业，标题叫第一次练习"
        );

        assertTrue(reply.isPresent());
        assertEquals("请补充：classId 或 className。", reply.orElseThrow().content());
        assertNull(reply.orElseThrow().pendingAction());
        verifyNoInteractions(planFactory);
    }

    @Test
    void courseCreateMissingCategoryUsesBusinessLabel() {
        AgentActionSpec spec = courseSpec();
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"course_create_plan\"]");
        LlmActionValidationResult validation = new LlmActionValidationResult(
                false,
                "course.create",
                Map.of("title", "Python 入门"),
                List.of("categoryId 或 categoryName"),
                List.of("缺少必填参数：[categoryId 或 categoryName]")
        );
        when(properties.isEnabled()).thenReturn(true);
        when(actionCatalog.enabledSpecs(List.of("course_create_plan"))).thenReturn(List.of(spec));
        when(actionCatalog.findByActionCode("course.create")).thenReturn(Optional.of(spec));
        when(repairLoop.plan("新增一门课，名字叫python入门", List.of(spec)))
                .thenReturn(new LlmActionPlanResult(actionDecision("course.create"), validation, 1, false));

        Optional<AgentDirectActionReply> reply = newPlanner().tryPlan(
                agent,
                "新增一门课，名字叫python入门"
        );

        assertEquals("请补充：课程分类。", reply.orElseThrow().content());
        assertNull(reply.orElseThrow().pendingAction());
        assertNotNull(reply.orElseThrow().clarificationDraft());
        assertEquals("course.create", reply.orElseThrow().clarificationDraft().actionCode());
        assertEquals("Python 入门", reply.orElseThrow().clarificationDraft().normalizedArguments().get("title"));
        verifyNoInteractions(planFactory);
    }

    @Test
    void continuesClarificationDraftIntoValidatedPendingPlan() {
        AgentActionSpec spec = courseSpec();
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"course_create_plan\"]");
        AgentActionClarificationDraft draft = AgentActionClarificationDraft.create(
                "course.create",
                "建一门课，名字叫测试",
                Map.of("title", "测试"),
                List.of("categoryId 或 categoryName"),
                Duration.ofMinutes(10)
        );
        LlmActionValidationResult validation = new LlmActionValidationResult(
                true,
                "course.create",
                Map.of("title", "测试", "categoryName", "计算机科学与技术"),
                List.of(),
                List.of()
        );
        AgentDirectActionReply pendingReply = new AgentDirectActionReply("动作待确认", null);
        String mergedRequest = "建一门课，名字叫测试；用户补充：课程分类计算机科学与技术";
        when(properties.isEnabled()).thenReturn(true);
        when(actionCatalog.enabledSpecs(List.of("course_create_plan"))).thenReturn(List.of(spec));
        when(repairLoop.continuePlan(draft, "课程分类计算机科学与技术", List.of(spec)))
                .thenReturn(new LlmActionPlanResult(actionDecision("course.create"), validation, 1, false));
        when(planFactory.create(mergedRequest, validation)).thenReturn(pendingReply);

        Optional<AgentDirectActionReply> reply = newPlanner().tryContinue(
                agent,
                draft,
                "课程分类计算机科学与技术"
        );

        assertEquals(pendingReply, reply.orElseThrow());
        verify(planFactory).create(mergedRequest, validation);
    }

    @Test
    void exhaustedRepairReturnsValidationFeedbackToUser() {
        AgentActionSpec spec = homeworkSpec();
        AgentDefinition agent = agentWithTools();
        LlmActionValidationResult validation = new LlmActionValidationResult(
                false,
                "homework.create",
                Map.of(),
                List.of(),
                List.of("操作规划服务未返回结果，请稍后重试。")
        );
        when(properties.isEnabled()).thenReturn(true);
        when(actionCatalog.enabledSpecs(List.of("homework_create_plan"))).thenReturn(List.of(spec));
        when(repairLoop.plan("发布作业", List.of(spec)))
                .thenReturn(new LlmActionPlanResult(actionDecision(), validation, 2, true));

        Optional<AgentDirectActionReply> reply = newPlanner().tryPlan(agent, "发布作业");

        assertEquals("操作参数校验已达到最大尝试次数（2 次）：操作规划服务未返回结果，请稍后重试。",
                reply.orElseThrow().content());
        assertNull(reply.orElseThrow().pendingAction());
        verifyNoInteractions(planFactory);
    }

    private SchemaDrivenAgentPlanner newPlanner() {
        return new SchemaDrivenAgentPlanner(properties, new ObjectMapper(), actionCatalog, repairLoop, planFactory);
    }

    private AgentDefinition agentWithTools() {
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"homework_create_plan\"]");
        return agent;
    }

    private LlmActionDecision actionDecision() {
        return actionDecision("homework.create");
    }

    private LlmActionDecision actionDecision(String actionCode) {
        LlmActionDecision decision = new LlmActionDecision();
        decision.setDecision("ACTION");
        decision.setActionCode(actionCode);
        decision.setConfidence(0.9);
        return decision;
    }

    private AgentActionSpec courseSpec() {
        return new AgentActionSpec(
                "course.create",
                "创建课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "创建课程",
                List.of(
                        new AgentActionParameterSpec("title", "课程名称", true),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", false),
                        new AgentActionParameterSpec("categoryName", "课程分类名称", false)
                ),
                List.of(List.of("categoryId", "categoryName")),
                List.of("会新增课程"),
                Duration.ofMinutes(10)
        );
    }

    private AgentActionSpec homeworkSpec() {
        return new AgentActionSpec(
                "homework.create",
                "发布作业",
                "homework",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "发布作业",
                List.of(new AgentActionParameterSpec("title", "作业标题", true)),
                List.of(List.of("classId", "className")),
                List.of("会新增作业"),
                Duration.ofMinutes(10)
        );
    }
}
