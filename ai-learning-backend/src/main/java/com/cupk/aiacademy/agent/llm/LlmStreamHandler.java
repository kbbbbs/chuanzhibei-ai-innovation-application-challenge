package com.cupk.aiacademy.agent.llm;

@FunctionalInterface
public interface LlmStreamHandler {
    void onDelta(String delta) throws Exception;
}
