package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class LlmActionRepairLoop {
    private final ChatModelClient chatModelClient;
    private final ActionSchemaPromptBuilder promptBuilder;
    private final LlmActionDecisionParser parser;
    private final LlmActionDecisionValidator validator;
    private final AgentPlannerProperties properties;

    public LlmActionRepairLoop(ChatModelClient chatModelClient,
                               ActionSchemaPromptBuilder promptBuilder,
                               LlmActionDecisionParser parser,
                               LlmActionDecisionValidator validator,
                               AgentPlannerProperties properties) {
        this.chatModelClient = chatModelClient;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.validator = validator;
        this.properties = properties;
    }

    private LlmActionDecision callModel(String promptText) {
        ChatResponse response = chatModelClient.call(new Prompt(List.of(new UserMessage(promptText))));
        String content = response.getResult().getOutput().getContent();
        return parser.parse(content);
    }

    public LlmActionPlanResult plan(String userText, List<AgentActionSpec> specs) {
        LlmActionDecision decision = null;
        LlmActionValidationResult validation = null;
        String prompt = promptBuilder.buildPlanPrompt(userText, specs);
        int maxAttempts = Math.max(1, properties.getMaxRepairRounds() + 1);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                decision = callModel(prompt);
                validation = validator.validate(decision, specs);
            } catch (Exception e) {
                validation = LlmActionValidationResult.invalid(
                        List.of("模型输出无法解析：" + safeMessage(e)));
            }

            if (validation.isValid()
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
            }
        }

        return new LlmActionPlanResult(decision, validation);
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
