package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentActionSchemaValidator;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.action.AgentActionSpecRegistry;
import com.cupk.aiacademy.agent.action.InMemoryAgentPendingActionStore;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.tool.course.CourseCreatePlanTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmActionRepairLoopTest {
    @Test
    void repairsMalformedJsonThenReturnsValidatedAction() {
        ChatModelClient model = mock(ChatModelClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        InMemoryAgentPendingActionStore pendingStore = new InMemoryAgentPendingActionStore();
        AgentActionCatalog catalog = new AgentActionCatalog(
                List.of(new CourseCreatePlanTool(new AgentActionPlanFactory(pendingStore))),
                new AgentActionSpecRegistry()
        );
        AgentActionSpec spec = catalog.findByActionCode("course.create").orElseThrow();
        AgentPlannerProperties properties = new AgentPlannerProperties();
        properties.setMaxRepairRounds(3);
        properties.setMinConfidence(0.6);
        when(model.call(any(Prompt.class)))
                .thenReturn(response("这不是 JSON"))
                .thenReturn(response("""
                        {"decision":"ACTION","actionCode":"course.create","arguments":{"title":"Java 入门","categoryName":"编程"},"missingRequiredFields":[],"userMessage":"","confidence":0.95}
                        """));

        LlmActionRepairLoop loop = new LlmActionRepairLoop(
                model,
                new ActionSchemaPromptBuilder(objectMapper),
                new LlmActionDecisionParser(objectMapper),
                new LlmActionDecisionValidator(catalog, new AgentActionSchemaValidator(), properties),
                properties
        );

        LlmActionPlanResult result = loop.plan("新增一门 Java 入门课，分类是编程", List.of(spec));

        assertTrue(result.getValidation().isValid());
        assertEquals("course.create", result.getValidation().getActionCode());
        assertEquals("Java 入门", result.getValidation().getNormalizedArguments().get("title"));
        verify(model, times(2)).call(any(Prompt.class));
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
