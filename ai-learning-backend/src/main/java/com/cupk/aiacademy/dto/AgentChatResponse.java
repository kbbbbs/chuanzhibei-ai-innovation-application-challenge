package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import com.cupk.aiacademy.rag.RagSearchHit;

@Data
public class AgentChatResponse {
    private Integer sessionId;
    private Integer userMessageId;
    private Integer assistantMessageId;
    private String content;
    private AgentPendingActionResponse pendingAction;
    private LocalDateTime createTime;
    private List<RagSearchHit> citations = List.of();
    private String retrievalMode;
    private String knowledgeStatus;
    private String knowledgeWarning;
}
