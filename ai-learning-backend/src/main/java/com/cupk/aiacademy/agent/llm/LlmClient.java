package com.cupk.aiacademy.agent.llm;

public interface LlmClient {
    String chat(LlmChatRequest request) throws Exception;

    void streamChat(LlmChatRequest request, LlmStreamHandler handler) throws Exception;
}
