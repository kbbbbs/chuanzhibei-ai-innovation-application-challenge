package com.cupk.aiacademy.agent.core.llm;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public interface ChatModelClient {
    ChatResponse call(Prompt prompt);

    Flux<ChatResponse> stream(Prompt prompt);
}
