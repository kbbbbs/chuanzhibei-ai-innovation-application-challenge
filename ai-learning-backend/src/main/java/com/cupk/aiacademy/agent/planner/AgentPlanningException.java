package com.cupk.aiacademy.agent.planner;

import java.util.List;

public class AgentPlanningException extends RuntimeException {
    private static final String DEFAULT_USER_MESSAGE = "操作规划失败，请稍后重试。";

    private final String userMessage;
    private final List<String> validationErrors;
    private final int attempts;

    public AgentPlanningException(String message) {
        this(message, null, DEFAULT_USER_MESSAGE, List.of(), 0);
    }

    public AgentPlanningException(String message, Throwable cause) {
        this(message, cause, DEFAULT_USER_MESSAGE, List.of(), 0);
    }

    public AgentPlanningException(String message,
                                  Throwable cause,
                                  String userMessage) {
        this(message, cause, userMessage, List.of(), 0);
    }

    public AgentPlanningException(String message,
                                  Throwable cause,
                                  String userMessage,
                                  List<String> validationErrors,
                                  int attempts) {
        super(message, cause);
        this.userMessage = userMessage == null || userMessage.isBlank()
                ? DEFAULT_USER_MESSAGE
                : userMessage.trim();
        this.validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        this.attempts = Math.max(0, attempts);
    }

    public String getUserMessage() {
        return userMessage;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public int getAttempts() {
        return attempts;
    }
}
