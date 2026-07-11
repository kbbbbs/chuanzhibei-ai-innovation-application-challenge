package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LlmActionRepairLoop {
    private static final Logger log = LoggerFactory.getLogger(LlmActionRepairLoop.class);

    private final PlannerModelClient plannerModelClient;
    private final ActionSchemaPromptBuilder promptBuilder;
    private final LlmActionDecisionParser parser;
    private final LlmActionDecisionValidator validator;
    private final AgentPlannerProperties properties;

    public LlmActionRepairLoop(PlannerModelClient plannerModelClient,
                               ActionSchemaPromptBuilder promptBuilder,
                               LlmActionDecisionParser parser,
                               LlmActionDecisionValidator validator,
                               AgentPlannerProperties properties) {
        this.plannerModelClient = plannerModelClient;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.validator = validator;
        this.properties = properties;
    }

    private LlmActionDecision callModel(String promptText) {
        return parser.parse(plannerModelClient.call(promptText, properties.getParameterTimeoutSeconds()));
    }

    public LlmActionPlanResult plan(String userText, List<AgentActionSpec> specs) {
        LlmActionDecision decision = null;
        LlmActionValidationResult validation = null;
        String prompt = promptBuilder.buildPlanPrompt(userText, specs);
        int maxAttempts = Math.max(1, properties.getMaxRepairRounds() + 1);
        int attempts = 0;
        boolean exhausted = false;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            attempts = attempt + 1;
            try {
                long startedNanos = System.nanoTime();
                decision = callModel(prompt);
                log.info("Agent planner stage completed: stage=write_parameters, model={}, attempt={}, promptChars={}, elapsedMs={}",
                        properties.getModel(), attempt + 1, prompt.length(), elapsedMillis(startedNanos));
                validation = validator.validate(decision, specs);
            } catch (AgentPlanningException e) {
                log.warn("Agent planner model call failed: stage=write_parameters, attempt={}, message={}",
                        attempt + 1, e.getMessage());
                validation = appendError(validation, e.getUserMessage());
            } catch (Exception e) {
                validation = appendError(validation, "模型输出格式不正确：" + safeMessage(e));
            }

            if (validation == null) {
                validation = LlmActionValidationResult.invalid(List.of("模型未返回可校验的操作参数"));
            }

            if (validation.isValid()
                    || requiresUserInput(validation)
                    || (decision != null && !isActionDecision(decision))) {
                break;
            }
            if (attempt + 1 < maxAttempts) {
                prompt = promptBuilder.buildRepairPrompt(
                        userText,
                        specs,
                        decision,
                        validation.getErrors()
                );
            } else {
                exhausted = true;
            }
        }

        return new LlmActionPlanResult(decision, validation, attempts, exhausted);
    }

    private boolean requiresUserInput(LlmActionValidationResult validation) {
        return validation.getMissingRequiredFields() != null
                && !validation.getMissingRequiredFields().isEmpty();
    }

    private LlmActionValidationResult appendError(LlmActionValidationResult previous, String error) {
        if (previous == null) {
            return LlmActionValidationResult.invalid(List.of(error));
        }
        List<String> errors = new ArrayList<>(previous.getErrors() == null ? List.of() : previous.getErrors());
        if (error != null && !error.isBlank() && !errors.contains(error.trim())) {
            errors.add(error.trim());
        }
        return new LlmActionValidationResult(
                false,
                previous.getActionCode(),
                previous.getNormalizedArguments(),
                previous.getMissingRequiredFields(),
                errors
        );
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "未知格式错误";
        }
        return exception.getMessage().trim();
    }

    private boolean isActionDecision(LlmActionDecision decision) {
        if (decision == null || decision.getDecision() == null) {
            return false;
        }
        String decisionType = decision.getDecision().trim().toUpperCase(Locale.ROOT);
        if ("ACTION".equals(decisionType)) {
            return true;
        }
        return "ASK_USER".equals(decisionType)
                && decision.getActionCode() != null
                && !decision.getActionCode().trim().isEmpty();
    }

}
