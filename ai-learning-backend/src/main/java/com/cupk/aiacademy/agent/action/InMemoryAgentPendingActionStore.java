package com.cupk.aiacademy.agent.action;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryAgentPendingActionStore implements AgentPendingActionStore {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final Map<String, AgentActionExecutionResult> results = new ConcurrentHashMap<>();

    private final Map<String, AgentPendingAction> actions = new ConcurrentHashMap<>();

    @Override
    public AgentPendingAction create(String actionCode, Integer createdBy, Map<String, Object> parameters, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        Duration effectiveTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
        AgentPendingAction action = new AgentPendingAction(
                UUID.randomUUID().toString(),
                actionCode,
                createdBy,
                parameters,
                now,
                now.plus(effectiveTtl)
        );
        actions.put(action.actionId(), action);
        return action;
    }

    @Override
    public Optional<AgentPendingAction> findById(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(actions.get(actionId.trim()));
    }

    @Override
    public Optional<AgentActionExecutionResult> lockAndReadResult(String actionId, Integer ownerId) {
        AgentPendingAction action = actions.get(actionId);
        if (action != null && !java.util.Objects.equals(action.createdBy(), ownerId))
            throw new com.cupk.aiacademy.common.BusinessException("不能确认其他用户创建的操作");
        return Optional.ofNullable(results.get(actionId));
    }

    @Override
    public void completed(String actionId, AgentActionExecutionResult result) { results.put(actionId, result); }

    @Override
    public void remove(String actionId) {
        if (actionId != null && !actionId.isBlank()) {
            actions.remove(actionId.trim());
        }
    }
}
