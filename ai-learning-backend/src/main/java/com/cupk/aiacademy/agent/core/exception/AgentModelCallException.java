package com.cupk.aiacademy.agent.core.exception;

import com.cupk.aiacademy.agent.core.llm.AgentModelDiagnostics;

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
    private final AgentModelDiagnostics diagnostics;

    public AgentModelCallException(String message,
                                   Integer sessionId,
                                   Integer userMessageId,
                                   Integer assistantMessageId,
                                   Throwable cause) {
        this(message, sessionId, userMessageId, assistantMessageId, null, cause);
    }

    public AgentModelCallException(String message,
                                   Integer sessionId,
                                   Integer userMessageId,
                                   Integer assistantMessageId,
                                   AgentModelDiagnostics diagnostics,
                                   Throwable cause) {
        super(message, cause);
        this.sessionId = sessionId;
        this.userMessageId = userMessageId;
        this.assistantMessageId = assistantMessageId;
        this.diagnostics = diagnostics;
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

    public AgentModelDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
