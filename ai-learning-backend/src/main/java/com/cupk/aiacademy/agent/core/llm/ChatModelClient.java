package com.cupk.aiacademy.agent.core.llm;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

public interface ChatModelClient {
    default String provider() {
        return "unknown";
    }

    default String configuredModel() {
        return "";
    }

    ChatResponse call(Prompt prompt);
}
