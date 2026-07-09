package com.cupk.aiacademy.agent.core.llm;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SpringAiChatModelClient implements ChatModelClient {
    private final OpenAiChatModel chatModel;
    private final String provider;
    private final String configuredModel;

    public SpringAiChatModelClient(OpenAiChatModel chatModel,
                                   @Value("${spring.ai.openai.base-url:}") String baseUrl,
                                   @Value("${spring.ai.openai.chat.options.model:}") String configuredModel) {
        this.chatModel = chatModel;
        this.provider = providerFromBaseUrl(baseUrl);
        this.configuredModel = configuredModel == null ? "" : configuredModel.trim();
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String configuredModel() {
        return configuredModel;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return chatModel.call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return chatModel.stream(prompt);
    }

    private String providerFromBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "spring-ai-openai";
        }
        String normalized = baseUrl.toLowerCase();
        if (normalized.contains("deepseek")) {
            return "deepseek";
        }
        if (normalized.contains("openai")) {
            return "openai";
        }
        return "openai-compatible";
    }
}
