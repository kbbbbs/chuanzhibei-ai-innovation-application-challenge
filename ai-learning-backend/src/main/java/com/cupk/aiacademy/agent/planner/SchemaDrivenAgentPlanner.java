package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionClarificationDraft;
import com.cupk.aiacademy.agent.action.AgentActionParameterSpec;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class SchemaDrivenAgentPlanner {
    private static final Logger log = LoggerFactory.getLogger(SchemaDrivenAgentPlanner.class);

    private final AgentPlannerProperties properties;
    private final AgentConfigurationParser configurationParser;
    private final AgentActionCatalog actionCatalog;
    private final LlmActionRepairLoop repairLoop;
    private final LlmValidatedPlanFactory planFactory;

    public SchemaDrivenAgentPlanner(AgentPlannerProperties properties,
                                    ObjectMapper objectMapper,
                                    AgentActionCatalog actionCatalog,
                                    LlmActionRepairLoop repairLoop,
                                    LlmValidatedPlanFactory planFactory) {
        this.properties = properties;
        this.configurationParser = new AgentConfigurationParser(objectMapper);
        this.actionCatalog = actionCatalog;
        this.repairLoop = repairLoop;
        this.planFactory = planFactory;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Optional<AgentDirectActionReply> tryPlan(AgentDefinition agent, String userText) {
        if (!properties.isEnabled() || agent == null || userText == null || userText.isBlank()) {
            return Optional.empty();
        }

        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        List<AgentActionSpec> specs = actionCatalog.enabledSpecs(enabledTools);
        if (specs.isEmpty()) {
            return Optional.empty();
        }

        return plan(userText.trim(), specs, false);
    }

    public Optional<AgentDirectActionReply> tryPlanSelected(AgentDefinition agent,
                                                            String userText,
                                                            String actionCode) {
        if (!properties.isEnabled() || agent == null || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        AgentActionSpec selected = actionCatalog.enabledSpecs(enabledTools).stream()
                .filter(spec -> normalize(spec.actionCode()).equals(normalize(actionCode)))
                .findFirst()
                .orElseThrow(() -> new AgentPlanningException(
                        "写操作不存在或未启用：" + actionCode,
                        null,
                        "当前 Agent 不支持这项操作。"
                ));
        return plan(userText.trim(), List.of(selected), true);
    }

    public Optional<AgentDirectActionReply> tryContinue(AgentDefinition agent,
                                                        AgentActionClarificationDraft draft,
                                                        String followUpText) {
        if (!properties.isEnabled() || agent == null || draft == null || draft.isExpired()
                || followUpText == null || followUpText.isBlank()) {
            return Optional.empty();
        }
        List<String> enabledTools = configurationParser.parseEnabledTools(agent.getEnabledTools());
        AgentActionSpec selected = actionCatalog.enabledSpecs(enabledTools).stream()
                .filter(spec -> normalize(spec.actionCode()).equals(normalize(draft.actionCode())))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return Optional.empty();
        }

        LlmActionPlanResult result = repairLoop.continuePlan(draft, followUpText.trim(), List.of(selected));
        String mergedRequest = draft.originalRequest() + "；用户补充：" + followUpText.trim();
        return replyFromResult(mergedRequest, result);
    }

    private Optional<AgentDirectActionReply> plan(String userText,
                                                  List<AgentActionSpec> specs,
                                                  boolean selectedIntent) {
        LlmActionPlanResult result;
        try {
            result = repairLoop.plan(userText, specs);
        } catch (AgentPlanningException e) {
            throw e;
        } catch (Exception e) {
            if (selectedIntent) {
                throw new AgentPlanningException("写操作参数规划失败", e);
            }
            log.warn("Schema-driven agent planner failed, continue without a write plan: message={}", e.getMessage(), e);
            return Optional.empty();
        }

        return replyFromResult(userText, result);
    }

    private Optional<AgentDirectActionReply> replyFromResult(String rawRequest,
                                                             LlmActionPlanResult result) {
        if (result == null || result.getValidation() == null) {
            return Optional.empty();
        }
        if (isChatDecision(result.getDecision())) {
            log.info("Scoped write planner vetoed selected or continued action and returned CHAT");
            return Optional.empty();
        }
        if (!result.getValidation().isValid()) {
            log.debug("Schema-driven agent planner requires user input: errors={}",
                    result.getValidation().getErrors());
            return Optional.of(clarificationReply(rawRequest, result));
        }
        return Optional.of(planFactory.create(rawRequest, result.getValidation()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isChatDecision(LlmActionDecision decision) {
        return decision != null
                && decision.getDecision() != null
                && "CHAT".equals(decision.getDecision().trim().toUpperCase(Locale.ROOT));
    }

    private AgentDirectActionReply clarificationReply(String originalRequest,
                                                       LlmActionPlanResult result) {
        LlmActionDecision decision = result.getDecision();
        LlmActionValidationResult validation = result.getValidation();
        List<String> missing = validation.getMissingRequiredFields();
        AgentActionClarificationDraft clarificationDraft = null;
        String message;
        if (missing != null && !missing.isEmpty()) {
            message = "请补充：" + String.join("、", humanizeMissingFields(validation.getActionCode(), missing)) + "。";
            AgentActionSpec spec = actionCatalog.findByActionCode(validation.getActionCode()).orElse(null);
            if (spec != null) {
                clarificationDraft = AgentActionClarificationDraft.create(
                        validation.getActionCode(),
                        originalRequest,
                        validation.getNormalizedArguments(),
                        missing,
                        spec.pendingTtl()
                );
            }
        } else if (result.isExhausted()) {
            int attempts = Math.max(1, result.getAttempts());
            message = "操作参数校验已达到最大尝试次数（" + attempts + " 次）："
                    + validationFailureMessage(validation.getErrors()) + "。";
        } else {
            message = decision == null ? null : decision.getUserMessage();
            if (message == null || message.isBlank()) {
                message = validationFailureMessage(validation.getErrors()) + "。";
            }
        }
        return new AgentDirectActionReply(conciseMessage(message), null, clarificationDraft);
    }

    private List<String> humanizeMissingFields(String actionCode, List<String> missingFields) {
        AgentActionSpec spec = actionCatalog.findByActionCode(actionCode).orElse(null);
        return missingFields.stream()
                .map(field -> humanizeMissingField(spec, field))
                .toList();
    }

    private String humanizeMissingField(AgentActionSpec spec, String missingField) {
        if (missingField == null || missingField.isBlank() || spec == null) {
            return missingField == null ? "必要信息" : missingField.trim();
        }
        String[] names = missingField.trim().split("\\s+或\\s+");
        List<String> labels = new java.util.ArrayList<>();
        for (String name : names) {
            AgentActionParameterSpec parameter = spec.parameter(name.trim());
            labels.add(parameter == null ? name.trim() : parameter.label());
        }
        if (labels.size() > 1) {
            List<String> bases = labels.stream().map(this::fieldLabelBase).toList();
            if (!bases.get(0).isBlank() && bases.stream().allMatch(bases.get(0)::equals)) {
                return bases.get(0);
            }
        }
        return String.join(" 或 ", labels);
    }

    private String fieldLabelBase(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().replaceFirst("(?i)(ID|编号|名称|名字|用户名|代码|标识)$", "").trim();
    }

    private String validationFailureMessage(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "请补充更明确的操作对象和参数";
        }
        for (String error : errors) {
            String value = error == null ? "" : error.trim();
            if (value.isBlank()) {
                continue;
            }
            if (value.startsWith("操作规划服务") || value.startsWith("操作规划超时")) {
                return trimEndingPunctuation(value);
            }
            if (value.contains("置信度不足")) {
                return "我还不能可靠判断操作参数，请补充更明确的信息";
            }
            if (value.contains("不存在或未启用")) {
                return "当前 Agent 不支持这项操作";
            }
            if (value.contains("模型输出") || value.contains("JSON") || value.contains("decision")) {
                return "模型返回的操作参数格式不正确，请重新描述操作";
            }
            int messageSeparator = value.indexOf(" - ");
            if (messageSeparator >= 0 && messageSeparator + 3 < value.length()) {
                return trimEndingPunctuation(value.substring(messageSeparator + 3));
            }
            return "操作参数未通过校验，请补充或修改后重试";
        }
        return "请补充更明确的操作对象和参数";
    }

    private String trimEndingPunctuation(String value) {
        return value == null ? "" : value.trim().replaceFirst("[。！？；;]+$", "");
    }

    private String conciseMessage(String message) {
        String value = message == null ? "" : message.trim().replaceAll("\\s+", " ");
        return value.length() <= 160 ? value : value.substring(0, 160) + "…";
    }
}
