package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
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
        assertTrue(reply.orElseThrow().content().contains("请补充具体教学班"));
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
        LlmActionDecision decision = new LlmActionDecision();
        decision.setDecision("ACTION");
        decision.setActionCode("homework.create");
        decision.setConfidence(0.9);
        return decision;
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
