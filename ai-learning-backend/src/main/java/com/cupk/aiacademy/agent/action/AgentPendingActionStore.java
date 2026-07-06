package com.cupk.aiacademy.agent.action;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public interface AgentPendingActionStore {
    AgentPendingAction create(String actionCode, Integer createdBy, Map<String, Object> parameters, Duration ttl);

    Optional<AgentPendingAction> findById(String actionId);

    void remove(String actionId);
}
