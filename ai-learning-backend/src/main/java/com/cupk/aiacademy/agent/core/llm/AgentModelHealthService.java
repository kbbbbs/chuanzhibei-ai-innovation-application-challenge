package com.cupk.aiacademy.agent.core.llm;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentModelHealthService {
    private final ChatModelClient chatModelClient;

    public AgentModelHealthService(ChatModelClient chatModelClient) {
        this.chatModelClient = chatModelClient;
    }

    public AgentModelHealthResponse check() {
        long started = System.currentTimeMillis();
        try {
            ChatResponse response = chatModelClient.call(new Prompt(List.of(
                    new SystemMessage("You are a health check endpoint. Reply with exactly: ok"),
                    new UserMessage("health check")
            )));
            long latencyMs = System.currentTimeMillis() - started;
            String sample = assistantContent(response);
            if (isBlank(sample)) {
                return AgentModelHealthResponse.unhealthy(provider(),
                        model(),
                        latencyMs,
                        AgentModelDiagnostics.emptyResponse(provider(), model()));
            }
            return AgentModelHealthResponse.healthy(provider(), model(), latencyMs, sample);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - started;
            return AgentModelHealthResponse.unhealthy(provider(),
                    model(),
                    latencyMs,
                    AgentModelDiagnostics.from(provider(), model(), e));
        }
    }

    private String assistantContent(ChatResponse response) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getContent() == null) {
            return "";
        }
        return response.getResult().getOutput().getContent().trim();
    }

    private String provider() {
        return chatModelClient.provider();
    }

    private String model() {
        return chatModelClient.configuredModel();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
