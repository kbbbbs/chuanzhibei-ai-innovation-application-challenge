package com.cupk.aiacademy.agent.core.llm;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SpringAiChatModelClient implements ChatModelClient {
    private final OpenAiChatModel chatModel;

    public SpringAiChatModelClient(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return chatModel.call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return chatModel.stream(prompt);
    }
}
