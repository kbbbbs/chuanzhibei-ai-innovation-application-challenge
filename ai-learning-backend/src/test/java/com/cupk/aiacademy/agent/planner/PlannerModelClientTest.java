package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlannerModelClientTest {
    @Test
    void appliesPlannerModelOptionsAndTimeout() {
        ChatModelClient delegate = mock(ChatModelClient.class);
        AgentPlannerProperties properties = new AgentPlannerProperties();
        properties.setModel("deepseek-v4-flash");
        properties.setTimeoutSeconds(5);
        properties.setMaxOutputTokens(600);
        properties.setTemperature(0.0);
        when(delegate.call(any(Prompt.class), any(Duration.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("{\"decision\":\"CHAT\"}")))));

        String content = new PlannerModelClient(delegate, properties).call("select intent");

        assertEquals("{\"decision\":\"CHAT\"}", content);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        ArgumentCaptor<Duration> timeoutCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(delegate).call(promptCaptor.capture(), timeoutCaptor.capture());
        assertEquals(Duration.ofSeconds(5), timeoutCaptor.getValue());
        assertEquals("deepseek-v4-flash", promptCaptor.getValue().getOptions().getModel());
        assertEquals(600, promptCaptor.getValue().getOptions().getMaxTokens());
        assertEquals(0.0, promptCaptor.getValue().getOptions().getTemperature());
    }
}
