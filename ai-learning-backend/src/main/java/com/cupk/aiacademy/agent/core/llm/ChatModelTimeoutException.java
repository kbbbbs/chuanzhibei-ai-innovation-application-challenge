package com.cupk.aiacademy.agent.core.llm;

public class ChatModelTimeoutException extends RuntimeException {
    public ChatModelTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
