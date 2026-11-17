package com.cupk.aiacademy.agent.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.pojo.OperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentActionExecutionService {
    private final Map<String, AgentActionExecutor> executorsByActionCode;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final AgentPendingActionStore pendingActionStore;
    private final AgentActionPreExecutionValidator preExecutionValidator;

    @Autowired
    public AgentActionExecutionService(List<AgentActionExecutor> executors,
                                       OperationLogMapper operationLogMapper,
                                       ObjectMapper objectMapper,
                                       AgentPendingActionStore pendingActionStore,
                                       AgentActionPreExecutionValidator preExecutionValidator) {
        this.executorsByActionCode = registerExecutors(executors);
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.pendingActionStore = pendingActionStore;
        this.preExecutionValidator = preExecutionValidator;
    }

    public AgentActionExecutionService(List<AgentActionExecutor> executors,
                                       OperationLogMapper operationLogMapper,
                                       ObjectMapper objectMapper,
                                       AgentPendingActionStore pendingActionStore) {
        this(executors, operationLogMapper, objectMapper, pendingActionStore, null);
    }

    @Transactional
    public synchronized AgentActionExecutionResult confirm(AgentActionConfirmRequest request, String clientIp) {
        if (request == null) {
            throw new BusinessException("确认请求不能为空");
        }
        if (!Boolean.TRUE.equals(request.confirmed())) {
            throw new BusinessException("写操作必须由用户确认后才能执行");
        }

        AuthUtils.LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException("请先登录后再确认执行");
        }

        if (request.actionId() == null) throw new BusinessException("请先生成操作预览，再确认执行");
        var completed = pendingActionStore.lockAndReadResult(request.actionId(), user.getUserId());
        if (completed.isPresent()) return completed.get();
        AgentPendingAction pendingAction = resolvePendingAction(request, user);
        pendingActionStore.validateTargets(pendingAction);
        Map<String, Object> parameters = mergeParameters(pendingAction, request.parameters());
        String actionCode = normalizeActionCode(pendingAction == null ? request.actionCode() : pendingAction.actionCode());
        if (actionCode.isEmpty()) {
            throw new BusinessException("actionCode 不能为空");
        }
        AgentActionExecutor executor = executorsByActionCode.get(actionCode);
        if (executor == null) {
            throw new BusinessException("不支持的 Agent 操作：" + actionCode);
        }
        if (preExecutionValidator != null) {
            parameters = preExecutionValidator.validateAndNormalize(actionCode, parameters);
        }
        pendingActionStore.validateExecutionParameters(pendingAction, parameters);
        requireHighRiskConfirmation(executor, request);

        AgentActionExecutionContext context = new AgentActionExecutionContext(user, parameters, clientIp);
        AgentActionExecutionResult result = executor.execute(context);
        writeAuditLog(user, executor, pendingAction == null ? request.actionId() : pendingAction.actionId(),
                parameters, result, clientIp);
        pendingActionStore.completed(pendingAction.actionId(), result);
        return result;
    }

    private AgentPendingAction resolvePendingAction(AgentActionConfirmRequest request, AuthUtils.LoginUser user) {
        if (request.actionId() == null) throw new BusinessException("请先生成操作预览");
        AgentPendingAction pendingAction = pendingActionStore.findById(request.actionId())
                .orElseThrow(() -> new BusinessException("待确认操作不存在或已被执行"));
        if (pendingAction.isExpired(LocalDateTime.now())) {
            pendingActionStore.remove(pendingAction.actionId());
            throw new BusinessException("待确认操作已过期，请重新发起");
        }
        if (pendingAction.createdBy() != null && !pendingAction.createdBy().equals(user.getUserId())) {
            throw new BusinessException("不能确认其他用户创建的操作");
        }
        String requestActionCode = normalizeActionCode(request.actionCode());
        String pendingActionCode = normalizeActionCode(pendingAction.actionCode());
        if (!requestActionCode.isEmpty() && !requestActionCode.equals(pendingActionCode)) {
            throw new BusinessException("确认操作与待执行计划不一致");
        }
        return pendingAction;
    }

    private Map<String, Object> mergeParameters(AgentPendingAction pendingAction, Map<String, Object> requestParameters) {
        Map<String,Object> reviewed = pendingAction.parameters();
        if (requestParameters != null) {
            for (var entry : requestParameters.entrySet()) {
                if (!objectMapper.valueToTree(entry.getValue()).equals(objectMapper.valueToTree(reviewed.get(entry.getKey()))))
                    throw new BusinessException("操作参数已改变，请重新预览后确认");
            }
        }
        return new LinkedHashMap<>(reviewed);
    }

    private void requireHighRiskConfirmation(AgentActionExecutor executor, AgentActionConfirmRequest request) {
        if (executor.riskLevel() != AgentActionRiskLevel.HIGH && executor.riskLevel() != AgentActionRiskLevel.CRITICAL) {
            return;
        }
        String expectedText = executor.confirmationText();
        if (expectedText == null || expectedText.isBlank()) {
            expectedText = "确认执行";
        }
        if (!expectedText.equals(request.confirmationText())) {
            throw new BusinessException("高风险操作需要输入确认文本：" + expectedText);
        }
    }

    private Map<String, AgentActionExecutor> registerExecutors(List<AgentActionExecutor> executors) {
        Map<String, AgentActionExecutor> registered = new LinkedHashMap<>();
        if (executors == null) {
            return registered;
        }
        for (AgentActionExecutor executor : executors) {
            String actionCode = normalizeActionCode(executor.actionCode());
            if (!actionCode.isEmpty()) {
                registered.putIfAbsent(actionCode, executor);
            }
        }
        return registered;
    }

    private void writeAuditLog(AuthUtils.LoginUser user,
                               AgentActionExecutor executor,
                               String actionId,
                               Map<String, Object> parameters,
                               AgentActionExecutionResult result,
                               String clientIp) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getUserId());
        log.setOperation("agent." + executor.actionCode());
        log.setMethod("POST");
        log.setUrl("/api/agent/actions/confirm");
        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("actionId", actionId);
        auditData.put("actionCode", executor.actionCode());
        auditData.put("parameters", parameters);
        auditData.put("result", result.data());
        log.setParams(toJson(auditData));
        log.setIp(clientIp);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String normalizeActionCode(String actionCode) {
        if (actionCode == null || actionCode.trim().isEmpty()) {
            return "";
        }
        return actionCode.trim().toLowerCase(Locale.ROOT);
    }
}
