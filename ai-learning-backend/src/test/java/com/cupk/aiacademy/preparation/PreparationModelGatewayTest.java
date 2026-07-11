package com.cupk.aiacademy.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreparationModelGatewayTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void parsesOutlineInsideMarkdownFence() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("```json\n{\"courseDescription\":\"测试课程\",\"chapters\":[{\"title\":\"第一章\",\"lessons\":[]}]}\n```"));
        PreparationModelGateway gateway = new PreparationModelGateway(model, new ObjectMapper(), executor, 5);

        PreparationModels.Outline outline = gateway.generateOutline("system", "user");

        assertEquals("测试课程", outline.getCourseDescription());
        assertEquals("第一章", outline.getChapters().getFirst().getTitle());
    }

    @Test
    void retriesOnceWhenOutlineJsonIsInvalid() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("not-json"), response("{\"courseDescription\":\"已修复\",\"chapters\":[]}"));
        PreparationModelGateway gateway = new PreparationModelGateway(model, new ObjectMapper(), executor, 5);

        assertEquals("已修复", gateway.generateOutline("system", "user").getCourseDescription());
    }

    @Test
    void removesOuterMarkdownFenceFromLesson() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("```markdown\n# 标题\n正文\n```"));
        PreparationModelGateway gateway = new PreparationModelGateway(model, new ObjectMapper(), executor, 5);

        String markdown = gateway.generateMarkdown("system", "user");

        assertTrue(markdown.startsWith("# 标题"));
        assertTrue(!markdown.endsWith("```"));
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
