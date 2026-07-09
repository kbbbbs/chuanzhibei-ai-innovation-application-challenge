package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionParameterValidationResult;
import com.cupk.aiacademy.agent.action.AgentActionSchemaValidator;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.action.AgentActionValidationIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LlmActionDecisionValidator {
    private final AgentActionCatalog actionCatalog;
    private final AgentActionSchemaValidator schemaValidator;
    private final AgentPlannerProperties properties;

    public LlmActionDecisionValidator(AgentActionCatalog actionCatalog,
                                      AgentActionSchemaValidator schemaValidator,
                                      AgentPlannerProperties properties) {
        this.actionCatalog = actionCatalog;
        this.schemaValidator = schemaValidator;
        this.properties = properties;
    }

    public LlmActionValidationResult validate(LlmActionDecision decision,
                                              List<AgentActionSpec> enabledSpecs) {
        if (decision == null) {
            return LlmActionValidationResult.invalid(List.of("模型未返回决策对象"));
        }

        List<String> errors = new ArrayList<>();
        String decisionType = upper(decision.getDecision());

        if ("CHAT".equals(decisionType)) {
            return LlmActionValidationResult.invalid(List.of("模型判断为普通聊天，不生成系统操作"));
        }

        if ("ASK_USER".equals(decisionType)) {
            String message = "模型判断信息不足";
            if (decision.getMissingRequiredFields() != null && !decision.getMissingRequiredFields().isEmpty()) {
                message += "：" + decision.getMissingRequiredFields();
            }
            return LlmActionValidationResult.invalid(List.of(message));
        }

        if (!"ACTION".equals(decisionType)) {
            errors.add("decision 必须是 ACTION、ASK_USER 或 CHAT");
        }

        Double confidence = decision.getConfidence();
        if (confidence == null || confidence < properties.getMinConfidence()) {
            errors.add("模型置信度不足，当前值=" + confidence + "，最低要求=" + properties.getMinConfidence());
        }

        String actionCode = normalizeActionCode(decision.getActionCode());
        if (actionCode.isBlank()) {
            errors.add("actionCode 不能为空");
            return LlmActionValidationResult.invalid(errors);
        }

        AgentActionSpec spec = actionCatalog.findByActionCode(actionCode).orElse(null);
        if (spec == null) {
            errors.add("actionCode 不存在：" + decision.getActionCode());
            return LlmActionValidationResult.invalid(errors);
        }

        if (!isActionEnabled(actionCode, enabledSpecs)) {
            errors.add("当前 Agent 未启用该 actionCode：" + actionCode);
        }

        Map<String, Object> rawArguments = decision.getArguments() == null ? Map.of() : decision.getArguments();

        AgentActionParameterValidationResult parameterResult =
                schemaValidator.validate(spec, rawArguments);

        for (AgentActionValidationIssue issue : parameterResult.issues()) {
            errors.add(issue.field() + ": " + issue.code() + " - " + issue.message());
        }

        if (!parameterResult.missingRequiredFields().isEmpty()) {
            errors.add("缺少必填参数：" + parameterResult.missingRequiredFields());
        }

        if (!errors.isEmpty()) {
            return new LlmActionValidationResult(
                    false,
                    actionCode,
                    parameterResult.parameters(),
                    parameterResult.missingRequiredFields(),
                    errors
            );
        }

        return new LlmActionValidationResult(
                true,
                actionCode,
                parameterResult.parameters(),
                List.of(),
                List.of()
        );
    }

    private boolean isActionEnabled(String actionCode, List<AgentActionSpec> enabledSpecs) {
        if (enabledSpecs == null || enabledSpecs.isEmpty()) {
            return false;
        }

        for (AgentActionSpec spec : enabledSpecs) {
            if (normalizeActionCode(spec.actionCode()).equals(actionCode)) {
                return true;
            }
        }

        return false;
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeActionCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}