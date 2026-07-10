package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.tool.homework.HomeworkCreatePlanTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionSpecRegistryTest {
    @Test
    void catalogUsesRegistrySpecForLegacyPlanTool() {
        AgentActionSpecRegistry registry = new AgentActionSpecRegistry();
        AgentActionCatalog catalog = new AgentActionCatalog(
                List.of(new HomeworkCreatePlanTool(new InMemoryAgentPendingActionStore())),
                registry
        );

        AgentActionSpec spec = catalog.findByToolName("homework_create_plan").orElseThrow();

        assertEquals("homework.create", spec.actionCode());
        assertEquals(List.of("classId 或 className 或 courseId 或 courseTitle"), spec.requiredConstraintLabels().stream()
                .filter(value -> value.contains("classId"))
                .toList());
        assertTrue(catalog.enabledActionSummaries(List.of("homework_create_plan")).getFirst()
                .containsKey("requiredConstraints"));
    }

    @Test
    void registryProvidesSpecsForOldDefaultTools() {
        AgentActionSpecRegistry registry = new AgentActionSpecRegistry();

        assertEquals("lesson.create", registry.findByToolName("lesson_create_plan").orElseThrow().actionCode());
        assertEquals("exam.paper.question.update",
                registry.findByToolName("exam_paper_question_update_plan").orElseThrow().actionCode());
        assertEquals("grade.record.create",
                registry.findByActionCode("grade.record.create").orElseThrow().actionCode());
    }
}
