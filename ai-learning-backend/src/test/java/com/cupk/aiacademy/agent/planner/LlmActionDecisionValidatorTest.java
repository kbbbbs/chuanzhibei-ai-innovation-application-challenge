package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionSchemaValidator;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.action.AgentActionSpecRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LlmActionDecisionValidatorTest {
    @Test
    void askUserWithActionCodeStillProducesIncompleteActionValidation() {
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(), new AgentActionSpecRegistry());
        AgentActionSpec homeworkCreate = catalog.findByActionCode("homework.create").orElseThrow();
        LlmActionDecisionValidator validator = new LlmActionDecisionValidator(
                catalog,
                new AgentActionSchemaValidator(),
                new AgentPlannerProperties()
        );
        LlmActionDecision decision = new LlmActionDecision();
        decision.setDecision("ASK_USER");
        decision.setActionCode("homework.create");
        decision.setArguments(Map.of(
                "title", "第一次练习",
                "deadline", "2026-07-17 20:00:00"
        ));
        decision.setConfidence(0.9);

        LlmActionValidationResult result = validator.validate(decision, List.of(homeworkCreate));

        assertFalse(result.isValid());
        assertEquals("homework.create", result.getActionCode());
        assertEquals(List.of("classId 或 className 或 courseId 或 courseTitle"), result.getMissingRequiredFields());
        assertEquals(List.of(
                "模型判断仍需用户补充信息",
                "缺少必填参数：[classId 或 className 或 courseId 或 courseTitle]"
        ), result.getErrors());
    }

    @Test
    void askUserWithoutActionCodeRemainsNonAction() {
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(), new AgentActionSpecRegistry());
        LlmActionDecisionValidator validator = new LlmActionDecisionValidator(
                catalog,
                new AgentActionSchemaValidator(),
                new AgentPlannerProperties()
        );
        LlmActionDecision decision = new LlmActionDecision();
        decision.setDecision("ASK_USER");
        decision.setMissingRequiredFields(List.of("actionCode"));

        LlmActionValidationResult result = validator.validate(decision, List.of());

        assertFalse(result.isValid());
        assertEquals(List.of("模型判断信息不足：[actionCode]"), result.getErrors());
    }
}
