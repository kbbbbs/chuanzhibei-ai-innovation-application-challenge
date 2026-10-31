package com.cupk.aiacademy.agent.action;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.AgentPendingActionMapper;
import com.cupk.aiacademy.pojo.AgentPendingActionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentPendingActionQueryService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentPendingActionMapper pendingActionMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, AgentActionExecutor> executorsByActionCode;

    public AgentPendingActionQueryService(AgentPendingActionMapper pendingActionMapper,
                                          ObjectMapper objectMapper,
                                          List<AgentActionExecutor> executors) {
        this.pendingActionMapper = pendingActionMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.executorsByActionCode = registerExecutors(executors);
    }

    @Transactional(readOnly = true)
    public List<AgentPendingActionResponse> listCurrentUserPending(Integer limit) {
        Integer userId = currentUserId();
        QueryWrapper<AgentPendingActionEntity> query = new QueryWrapper<>();
        query.eq("created_by", userId)
                .gt("expires_at", LocalDateTime.now())
                .orderByDesc("created_at")
                .last("LIMIT " + clampLimit(limit));
        return pendingActionMapper.selectList(query).stream()
                .map(entity -> toResponse(entity, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AgentPendingActionResponse getCurrentUserPending(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            throw new BusinessException("actionId 不能为空");
        }
        Integer userId = currentUserId();
        AgentPendingActionEntity entity = pendingActionMapper.selectById(actionId.trim());
        if (entity == null) {
            throw new BusinessException("待确认操作不存在或已被执行");
        }
        if (entity.getCreatedBy() != null && !entity.getCreatedBy().equals(userId)) {
            throw new BusinessException("无权限查看该待确认操作");
        }
        AgentPendingAction action = toDomain(entity);
        if (action.isExpired(LocalDateTime.now())) {
            throw new BusinessException("待确认操作已过期，请重新发起");
        }
        return toResponse(entity, true);
    }

    private AgentPendingActionResponse toResponse(AgentPendingActionEntity entity, boolean includeParameters) {
        AgentPendingAction action = toDomain(entity);
        return AgentPendingActionResponse.fromDomain(action,
                executorsByActionCode.get(normalize(action.actionCode())),
                includeParameters);
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

    private Map<String, Object> parseParameters(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Integer currentUserId() {
        AuthUtils.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException("请先登录后再查看待确认操作");
        }
        return user.getUserId();
    }

    private int clampLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        return Math.max(1, Math.min(MAX_LIMIT, value));
    }

    private Map<String, AgentActionExecutor> registerExecutors(List<AgentActionExecutor> executors) {
        Map<String, AgentActionExecutor> registered = new LinkedHashMap<>();
        if (executors == null) {
            return registered;
        }
        for (AgentActionExecutor executor : executors) {
            String actionCode = normalize(executor.actionCode());
            if (!actionCode.isBlank()) {
                registered.putIfAbsent(actionCode, executor);
            }
        }
        return registered;
    }

    private String normalize(String actionCode) {
        return actionCode == null ? "" : actionCode.trim().toLowerCase(Locale.ROOT);
    }
}
