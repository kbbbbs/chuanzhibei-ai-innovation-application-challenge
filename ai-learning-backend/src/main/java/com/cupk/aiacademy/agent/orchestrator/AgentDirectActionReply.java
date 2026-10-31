package com.cupk.aiacademy.agent.orchestrator;

import com.cupk.aiacademy.agent.action.AgentActionClarificationDraft;
import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;

public record AgentDirectActionReply(String content,
                                     AgentPendingActionResponse pendingAction,
                                     AgentActionClarificationDraft clarificationDraft) {
    public AgentDirectActionReply(String content, AgentPendingActionResponse pendingAction) {
        this(content, pendingAction, null);
    }
}
