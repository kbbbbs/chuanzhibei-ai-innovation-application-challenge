package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.core.llm.ChatModelTimeoutException;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class PlannerModelClient {
    private final ChatModelClient chatModelClient;
    private final AgentPlannerProperties properties;

    public PlannerModelClient(ChatModelClient chatModelClient,
                              AgentPlannerProperties properties) {
        this.chatModelClient = chatModelClient;
        this.properties = properties;
    }

    public String call(String promptText) {
        return call(promptText, properties.getTimeoutSeconds());
    }

    public String call(String promptText, int timeoutSeconds) {
        try {
            ChatResponse response = chatModelClient.call(plannerPrompt(promptText),
                    Duration.ofSeconds(Math.max(1, timeoutSeconds)));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new AgentPlanningException("规划模型未返回内容");
            }
            String content = response.getResult().getOutput().getContent();
            if (content == null || content.isBlank()) {
                throw new AgentPlanningException("规划模型返回为空");
            }
            return content.trim();
        } catch (AgentPlanningException e) {
            throw e;
        } catch (ChatModelTimeoutException e) {
            throw new AgentPlanningException("Agent 规划超时，请重新发送", e);
        } catch (Exception e) {
            throw new AgentPlanningException("Agent 规划模型调用失败：" + safeMessage(e), e);
        }
    }

    private Prompt plannerPrompt(String promptText) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .temperature(properties.getTemperature())
                .maxTokens(Math.max(100, properties.getMaxOutputTokens()));
        if (properties.getModel() != null && !properties.getModel().isBlank()) {
            options.model(properties.getModel().trim());
        }
        return new Prompt(List.of(new UserMessage(promptText)), options.build());
    }

    private String safeMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage().trim();
    }
}
