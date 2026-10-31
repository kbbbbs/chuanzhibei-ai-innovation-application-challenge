package com.cupk.aiacademy.agent.core.llm;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;

public interface ChatModelClient {
    default String provider() {
        return "unknown";
    }

    default String configuredModel() {
        return "";
    }

    ChatResponse call(Prompt prompt);

    default ChatResponse call(Prompt prompt, Duration timeout) {
        return call(prompt);
    }

    /** Planning has its own endpoint/model; compatibility defaults support test doubles. */
    default ChatResponse callPlanning(Prompt prompt, Duration timeout) { return call(prompt, timeout); }

    default ChatResponse callPlanning(Prompt prompt) { return callPlanning(prompt, Duration.ofSeconds(10)); }

    Flux<ChatResponse> stream(Prompt prompt);
}
