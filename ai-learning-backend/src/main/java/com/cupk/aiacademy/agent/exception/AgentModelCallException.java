package com.cupk.aiacademy.agent.exception;

/**
 * Agent 模型调用失败异常。
 * <p>
 * 会话聊天失败时，服务层会先落库用户消息和助手失败提示，
 * 再抛出该异常，由 AgentExceptionHandler 返回统一 Result 错误响应。
 */
public class AgentModelCallException extends RuntimeException {
    private final Integer sessionId;
    private final Integer userMessageId;
    private final Integer assistantMessageId;

    public AgentModelCallException(String message,
                                   Integer sessionId,
                                   Integer userMessageId,
                                   Integer assistantMessageId,
                                   Throwable cause) {
        super(message, cause);
        this.sessionId = sessionId;
        this.userMessageId = userMessageId;
        this.assistantMessageId = assistantMessageId;
    }

    public AgentModelCallException(String message,
                                   Integer sessionId,
                                   Integer userMessageId,
                                   Integer assistantMessageId) {
        this(message, sessionId, userMessageId, assistantMessageId, null);
    }

    public Integer getSessionId() {
        return sessionId;
    }

    public Integer getUserMessageId() {
        return userMessageId;
    }

    public Integer getAssistantMessageId() {
        return assistantMessageId;
    }
}
