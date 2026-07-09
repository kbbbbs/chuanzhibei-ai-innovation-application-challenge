package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentChatResponse {
    private Integer sessionId;
    private Integer userMessageId;
    private Integer assistantMessageId;
    private String content;
    private AgentPendingActionResponse pendingAction;
    private LocalDateTime createTime;
}
