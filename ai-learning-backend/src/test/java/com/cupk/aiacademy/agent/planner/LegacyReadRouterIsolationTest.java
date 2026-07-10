package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.core.service.AgentChatService;
import com.cupk.aiacademy.agent.action.AgentActionSelectionService;
import com.cupk.aiacademy.agent.orchestrator.AgentActionIntentRouter;
import com.cupk.aiacademy.agent.query.NaturalLanguageQueryService;
import com.cupk.aiacademy.agent.query.NaturalLanguageEntityResolver;
import com.cupk.aiacademy.agent.query.NaturalLanguageTimeParser;
import com.cupk.aiacademy.agent.tool.query.NaturalLanguageQueryTool;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyReadRouterIsolationTest {
    @Test
    void legacyRuleQueryIsNotAProductionBeanOrDefaultTool() throws Exception {
        assertFalse(NaturalLanguageQueryTool.class.isAnnotationPresent(Component.class));
        assertFalse(NaturalLanguageQueryService.class.isAnnotationPresent(Service.class));
        assertFalse(NaturalLanguageEntityResolver.class.isAnnotationPresent(Component.class));
        assertFalse(NaturalLanguageTimeParser.class.isAnnotationPresent(Component.class));
        assertFalse(AgentActionSelectionService.class.isAnnotationPresent(Component.class));
        assertFalse(AgentActionIntentRouter.class.isAnnotationPresent(Component.class));

        Field defaultsField = AgentChatService.class.getDeclaredField("DEFAULT_AGENT_ENABLED_TOOLS");
        defaultsField.setAccessible(true);
        String defaults = String.valueOf(defaultsField.get(null));
        assertFalse(defaults.contains("natural_language_query"));
        assertTrue(defaults.contains("teacher_application_search"));
        assertTrue(defaults.contains("homework_submission_stats"));
        assertTrue(defaults.contains("course_structure_search"));
    }
}
