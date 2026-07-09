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
        LlmActionDecision decision = callModel(promptBuilder.buildPlanPrompt(userText, specs));
        LlmActionValidationResult validation = validator.validate(decision, specs);
        if (!isActionDecision(decision)) {
            return new LlmActionPlanResult(decision, validation);
        }

        int round = 0;
        while (!validation.isValid() && round < properties.getMaxRepairRounds()) {
            String repairPrompt = promptBuilder.buildRepairPrompt(userText, specs, decision, validation.getErrors());
            decision = callModel(repairPrompt);
            validation = validator.validate(decision, specs);
            if (!isActionDecision(decision)) {
                break;
            }
            round++;
        }

        return new LlmActionPlanResult(decision, validation);
    }

    private boolean isActionDecision(LlmActionDecision decision) {
        if (decision == null || decision.getDecision() == null) {
            return false;
        }
        return "ACTION".equals(decision.getDecision().trim().toUpperCase(Locale.ROOT));
    }

}
