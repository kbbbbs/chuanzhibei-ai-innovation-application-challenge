package com.cupk.aiacademy.agent.orchestrator;

import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;

public record AgentDirectActionReply(String content, AgentPendingActionResponse pendingAction) {
}
