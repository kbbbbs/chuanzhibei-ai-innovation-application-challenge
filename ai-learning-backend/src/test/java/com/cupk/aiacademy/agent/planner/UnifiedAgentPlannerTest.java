package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.pojo.AgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnifiedAgentPlannerTest {
    @Test
    void writeSelectionSendsOnlySelectedActionToScopedPlanner() {
        LlmIntentSelector selector = mock(LlmIntentSelector.class);
        SchemaDrivenAgentPlanner writePlanner = mock(SchemaDrivenAgentPlanner.class);
        SchemaDrivenReadToolPlanner readPlanner = mock(SchemaDrivenReadToolPlanner.class);
        AgentDefinition agent = new AgentDefinition();
        AgentDirectActionReply reply = new AgentDirectActionReply("待确认", null);
        when(selector.select(agent, "布置作业"))
                .thenReturn(AgentIntentSelection.write("homework.create"));
        when(writePlanner.tryPlanSelected(agent, "布置作业", "homework.create"))
                .thenReturn(Optional.of(reply));

        AgentPlanningOutcome outcome = new UnifiedAgentPlanner(selector, writePlanner, readPlanner)
                .plan(agent, "布置作业");

        assertEquals(reply, outcome.getDirectReply());
        assertEquals("", outcome.getToolContext());
        verify(writePlanner).tryPlanSelected(agent, "布置作业", "homework.create");
        verifyNoInteractions(readPlanner);
    }

    @Test
    void readSelectionSendsOnlySelectedToolToArgumentPlanner() {
        LlmIntentSelector selector = mock(LlmIntentSelector.class);
        SchemaDrivenAgentPlanner writePlanner = mock(SchemaDrivenAgentPlanner.class);
        SchemaDrivenReadToolPlanner readPlanner = mock(SchemaDrivenReadToolPlanner.class);
        AgentDefinition agent = new AgentDefinition();
        when(selector.select(agent, "现在有哪些课程"))
                .thenReturn(AgentIntentSelection.read("course_search"));
        when(readPlanner.planSelectedToolContext(agent, "现在有哪些课程", "course_search"))
                .thenReturn("[course_search]\nJava 入门");

        AgentPlanningOutcome outcome = new UnifiedAgentPlanner(selector, writePlanner, readPlanner)
                .plan(agent, "现在有哪些课程");

        assertNull(outcome.getDirectReply());
        assertEquals("[course_search]\nJava 入门", outcome.getToolContext());
        verify(readPlanner).planSelectedToolContext(agent, "现在有哪些课程", "course_search");
        verifyNoInteractions(writePlanner);
    }

    @Test
    void scopedWritePlannerCanSafelyVetoFalsePositiveIntent() {
        LlmIntentSelector selector = mock(LlmIntentSelector.class);
        SchemaDrivenAgentPlanner writePlanner = mock(SchemaDrivenAgentPlanner.class);
        SchemaDrivenReadToolPlanner readPlanner = mock(SchemaDrivenReadToolPlanner.class);
        AgentDefinition agent = new AgentDefinition();
        when(selector.select(agent, "请只回复测试通过"))
                .thenReturn(AgentIntentSelection.write("teacher.application.review"));
        when(writePlanner.tryPlanSelected(agent, "请只回复测试通过", "teacher.application.review"))
                .thenReturn(Optional.empty());

        AgentPlanningOutcome outcome = new UnifiedAgentPlanner(selector, writePlanner, readPlanner)
                .plan(agent, "请只回复测试通过");

        assertNull(outcome.getDirectReply());
        assertEquals("", outcome.getToolContext());
        verifyNoInteractions(readPlanner);
    }
}
