package com.cupk.aiacademy.agent.action;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public interface AgentPendingActionStore {
    AgentPendingAction create(String actionCode, Integer createdBy, Map<String, Object> parameters, Duration ttl);

    Optional<AgentPendingAction> findById(String actionId);

    void remove(String actionId);

    default void bindToSession(String actionId, Integer sessionId, Integer courseId) {}

    default Optional<AgentActionExecutionResult> lockAndReadResult(String actionId, Integer ownerId) {
        return Optional.empty();
    }

    default void validateTargets(AgentPendingAction action) {}

    default void validateExecutionParameters(AgentPendingAction action, Map<String,Object> parameters) {}

    default void completed(String actionId, AgentActionExecutionResult result) {}

}
