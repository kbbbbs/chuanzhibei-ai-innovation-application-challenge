package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentActionSelectionServiceTest {
    @Test
    void selectMapsModelActionCodeToEnabledToolName() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        when(chatModelClient.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"actionCode":"course.create","confidence":0.91,"needsClarification":false}
                """));
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(
                tool("course_create_plan", "course_add_plan", spec("course.create", "创建课程")),
                tool("chapter_create_plan", "chapter_add_plan", spec("chapter.create", "创建章节"))
        ));
        AgentActionSelectionService service = new AgentActionSelectionService(
                new ObjectMapper(),
                chatModelClient,
                catalog
        );

        Optional<AgentActionSelectionResult> result = service.select(
                List.of("course_add_plan", "chapter_create_plan"),
                "新增一门课，名字叫 Java 入门"
        );

        assertTrue(result.isPresent());
        assertEquals("course.create", result.get().actionCode());
        assertEquals("course_add_plan", result.get().toolName());
    }

    @Test
    void selectIgnoresLowConfidenceResult() {
        ChatModelClient chatModelClient = mock(ChatModelClient.class);
        when(chatModelClient.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"actionCode":"course.create","confidence":0.2,"needsClarification":true}
                """));
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(
                tool("course_create_plan", "course_add_plan", spec("course.create", "创建课程"))
        ));
        AgentActionSelectionService service = new AgentActionSelectionService(
                new ObjectMapper(),
                chatModelClient,
                catalog
        );

        Optional<AgentActionSelectionResult> result = service.select(
                List.of("course_create_plan"),
                "看看课程情况"
        );

        assertTrue(result.isEmpty());
    }

    private AgentTool tool(String name, String alias, AgentActionSpec spec) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Set<String> aliases() {
                return Set.of(alias);
            }

            @Override
            public Optional<AgentActionSpec> actionSpec() {
                return Optional.of(spec);
            }

            @Override
            public AgentToolResult execute(AgentToolContext context) {
                return null;
            }
        };
    }

    private AgentActionSpec spec(String actionCode, String actionName) {
        return new AgentActionSpec(
                actionCode,
                actionName,
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备执行动作",
                List.of(new AgentActionParameterSpec("title", "标题", false)),
                List.of(),
                Duration.ofMinutes(10)
        );
    }

    private ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
