package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.action.AgentPendingAction;
import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;
import com.cupk.aiacademy.agent.action.AgentPendingActionStore;
import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.common.UserContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LlmValidatedPlanFactory {
    private final AgentActionCatalog actionCatalog;
    private final AgentPendingActionStore pendingActionStore;

    public LlmValidatedPlanFactory(AgentActionCatalog actionCatalog,
                                   AgentPendingActionStore pendingActionStore) {
        this.actionCatalog = actionCatalog;
        this.pendingActionStore = pendingActionStore;
    }

    public AgentDirectActionReply create(String rawRequest, LlmActionValidationResult validation) {
        AgentActionSpec spec = actionCatalog.findByActionCode(validation.getActionCode())
                .orElseThrow(() -> new IllegalArgumentException("不支持的 actionCode：" + validation.getActionCode()));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", rawRequest);
        parameters.putAll(validation.getNormalizedArguments());
        parameters.put("requiredFields", spec.requiredFieldNames());
        parameters.put("requiredConstraints", spec.requiredConstraintLabels());
        parameters.put("requiredAnyOf", spec.requiredAnyOf());
        parameters.put("optionalFields", spec.optionalFieldNames());
        parameters.put("missingRequiredFields", validation.getMissingRequiredFields());
        parameters.put("parameterSchema", spec.parameterSchema());

        Integer userId = UserContext.getCurrentUserId();
        AgentPendingAction pendingAction = pendingActionStore.create(
                spec.actionCode(),
                userId,
                parameters,
                spec.pendingTtl()
        );

        Map<String, Object> data = new LinkedHashMap<>(spec.toData());
        data.put("actionId", pendingAction.actionId());
        data.put("createdBy", pendingAction.createdBy());
        data.put("createdAt", pendingAction.createdAt().toString());
        data.put("expiresAt", pendingAction.expiresAt().toString());
        data.put("parameters", parameters);

        String content = "已根据模型规划生成待确认操作；"
                + "操作=" + spec.actionName()
                + "；actionCode=" + spec.actionCode()
                + "；确认ID=" + pendingAction.actionId();
        if (validation.getMissingRequiredFields() != null && !validation.getMissingRequiredFields().isEmpty()) {
            content += "；待补齐字段=" + String.join("、", validation.getMissingRequiredFields());
        }
        content += "。请核对后确认执行。";

        return new AgentDirectActionReply(content, AgentPendingActionResponse.fromToolData(data));
    }
}
