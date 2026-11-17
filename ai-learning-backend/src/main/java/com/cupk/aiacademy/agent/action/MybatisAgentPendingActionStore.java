package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.mapper.AgentPendingActionMapper;
import com.cupk.aiacademy.pojo.AgentPendingActionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Primary
@Component
public class MybatisAgentPendingActionStore implements AgentPendingActionStore {
    private static final Logger log = LoggerFactory.getLogger(MybatisAgentPendingActionStore.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @org.springframework.beans.factory.annotation.Autowired
    private AgentActionSafetyService safety;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<AgentActionPreExecutionValidator> validatorProvider;

    private final AgentPendingActionMapper pendingActionMapper;
    private final ObjectMapper objectMapper;

    public MybatisAgentPendingActionStore(AgentPendingActionMapper pendingActionMapper,
                                          ObjectMapper objectMapper) {
        this.pendingActionMapper = pendingActionMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public AgentPendingAction create(String actionCode, Integer createdBy, Map<String, Object> parameters, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        Duration effectiveTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
        Map<String,Object> reviewed = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);
        if (validatorProvider != null) {
            AgentActionPreExecutionValidator validator = validatorProvider.getIfAvailable();
            if (validator != null) reviewed.putAll(validator.validateAndNormalize(actionCode, reviewed));
        }
        AgentPendingAction action = new AgentPendingAction(
                UUID.randomUUID().toString(),
                actionCode,
                createdBy,
                reviewed,
                now,
                now.plus(effectiveTtl)
        );

        AgentPendingActionEntity entity = toEntity(action);
        pendingActionMapper.insert(entity);
        if (safety != null) safety.register(action);
        return action;
    }

    @Override
    public Optional<AgentPendingAction> findById(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return Optional.empty();
        }
        AgentPendingActionEntity entity = pendingActionMapper.selectById(actionId.trim());
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void remove(String actionId) {
        if (actionId != null && !actionId.isBlank()) {
            pendingActionMapper.deleteById(actionId.trim());
        }
    }

    @Override
    public void bindToSession(String actionId, Integer sessionId, Integer courseId) {
        safety.bind(actionId, sessionId, courseId);
    }

    @Override
    public Optional<AgentActionExecutionResult> lockAndReadResult(String actionId, Integer ownerId) {
        return safety.lockAndReadResult(actionId, ownerId);
    }

    @Override
    public void validateTargets(AgentPendingAction action) { safety.validateTargets(action); }

    @Override
    public void validateExecutionParameters(AgentPendingAction action, Map<String,Object> parameters) {
        safety.validateExecutionParameters(action, parameters);
    }

    @Override
    public void completed(String actionId, AgentActionExecutionResult result) { safety.completed(actionId, result); }

    private AgentPendingActionEntity toEntity(AgentPendingAction action) {
        AgentPendingActionEntity entity = new AgentPendingActionEntity();
        entity.setActionId(action.actionId());
        entity.setActionCode(action.actionCode());
        entity.setCreatedBy(action.createdBy());
        entity.setParametersJson(toJson(action.parameters()));
        entity.setCreatedAt(action.createdAt());
        entity.setExpiresAt(action.expiresAt());
        return entity;
    }

    private AgentPendingAction toDomain(AgentPendingActionEntity entity) {
        return new AgentPendingAction(
                entity.getActionId(),
                entity.getActionCode(),
                entity.getCreatedBy(),
                parseParameters(entity.getParametersJson()),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }

    private String toJson(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters == null ? Map.of() : parameters);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize Agent pending action parameters", e);
            return "{}";
        }
    }

    private Map<String, Object> parseParameters(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse Agent pending action parameters: {}", value, e);
            return new LinkedHashMap<>();
        }
    }
}
