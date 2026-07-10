package com.cupk.aiacademy.agent.core.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiChatModelClientTest {
    @Test
    void cancelsModelCallAfterRequestedTimeout() throws Exception {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(10_000);
            return null;
        });
        SpringAiChatModelClient client = new SpringAiChatModelClient(
                model, "https://api.deepseek.com", "deepseek-v4-pro", 15);
        try {
            assertThrows(ChatModelTimeoutException.class,
                    () -> client.call(new Prompt("hello"), Duration.ofMillis(50)));
        } finally {
            client.shutdown();
        }
    }
}
