package com.cupk.aiacademy.agent.core.prompt;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.knowledge.KnowledgeContextProvider;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.pojo.AgentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the model prompt from Agent definition, chat history, knowledge and
 * platform tool context.
 */
public class AgentPromptAssembler {
    private static final Logger log = LoggerFactory.getLogger(AgentPromptAssembler.class);
    private static final String SYSTEM_PROMPT = "你是一个智能教学管理平台助手";

    private final AgentChatProperties chatProperties;
    private final AgentConfigurationParser configurationParser;
    private final KnowledgeContextProvider knowledgeContextProvider;

    public AgentPromptAssembler(AgentChatProperties chatProperties,
                                AgentConfigurationParser configurationParser,
                                KnowledgeContextProvider knowledgeContextProvider) {
        this.chatProperties = chatProperties == null ? new AgentChatProperties() : chatProperties;
        this.configurationParser = configurationParser == null
                ? new AgentConfigurationParser(null)
                : configurationParser;
        this.knowledgeContextProvider = knowledgeContextProvider;
    }

    public Prompt buildPrompt(AgentDefinition agent,
                              List<AgentMessage> historyMessages,
                              String currentUserContent) {
        return buildPrompt(agent, historyMessages, currentUserContent, "");
    }

    public Prompt buildPrompt(AgentDefinition agent,
                              List<AgentMessage> historyMessages,
                              String currentUserContent,
                              String selectedToolContext) {
        String knowledgeContext = retrieveKnowledgeContext(agent, currentUserContent);
        List<Message> messages = buildPromptMessages(historyMessages,
                systemPrompt(agent, knowledgeContext, selectedToolContext),
                currentUserContent);

        OpenAiChatOptions options = chatOptions(agent);
        return options == null ? new Prompt(messages) : new Prompt(messages, options);
    }

    private List<Message> buildPromptMessages(List<AgentMessage> historyMessages,
                                              String systemPrompt,
                                              String currentUserContent) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt));

        for (AgentMessage message : selectContextMessages(historyMessages)) {
            if ("user".equals(message.getRole())) {
                promptMessages.add(new UserMessage(message.getContent()));
            } else if ("assistant".equals(message.getRole())) {
                promptMessages.add(new AssistantMessage(message.getContent()));
            }
        }
        promptMessages.add(new UserMessage(currentUserContent.trim()));
        return promptMessages;
    }

    private List<AgentMessage> selectContextMessages(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()
                || chatProperties.getHistoryMessageLimit() <= 0
                || chatProperties.getHistoryMaxCharacters() <= 0) {
            return List.of();
        }

        List<AgentMessage> selected = new ArrayList<>();
        int usedCharacters = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (!isPromptRole(message.getRole()) || isBlank(message.getContent())) {
                continue;
            }
            int contentLength = message.getContent().trim().length();
            if (usedCharacters + contentLength > chatProperties.getHistoryMaxCharacters()) {
                break;
            }
            selected.add(message);
            usedCharacters += contentLength;
            if (selected.size() >= chatProperties.getHistoryMessageLimit()) {
                break;
            }
        }
        Collections.reverse(selected);
        return selected;
    }

    private boolean isPromptRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    private String systemPrompt(AgentDefinition agent) {
        return firstNotBlank(agent.getSystemPrompt(), SYSTEM_PROMPT);
    }

    private String systemPrompt(AgentDefinition agent, String knowledgeContext, String toolContext) {
        StringBuilder prompt = new StringBuilder(systemPrompt(agent));
        if (hasWritePlanTools(agent)) {
            prompt.append("\n\n重要规则：不要编造 actionId、确认ID、待确认操作记录或执行结果。")
                    .append("如果本轮没有真实工具调用结果，就只能说明需要通过系统支持的待确认操作入口生成计划。");
        }
        if (!isBlank(knowledgeContext)) {
            prompt.append("\n\n知识库参考资料（优先基于资料回答；资料不足时说明无法从资料中确认）：\n")
                    .append(knowledgeContext);
        }
        if (!isBlank(toolContext)) {
            prompt.append("\n\n工具调用结果（优先结合这些平台数据回答；数据不足时说明限制）：\n")
                    .append(toolContext);
        }
        return prompt.toString();
    }

    private boolean hasWritePlanTools(AgentDefinition agent) {
        return configurationParser.parseEnabledTools(agent.getEnabledTools()).stream()
                .anyMatch(toolName -> toolName != null && toolName.trim().endsWith("_plan"));
    }

    private String retrieveKnowledgeContext(AgentDefinition agent, String currentUserContent) {
        if (knowledgeContextProvider == null) {
            return "";
        }

        try {
            return knowledgeContextProvider.retrieve(agent, currentUserContent);
        } catch (Exception e) {
            log.warn("Agent knowledge search failed, continue without knowledge context", e);
            return "";
        }
    }

    private OpenAiChatOptions chatOptions(AgentDefinition agent) {
        boolean hasOptions = false;
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (!isBlank(agent.getModel())) {
            builder.model(agent.getModel().trim());
            hasOptions = true;
        }
        if (agent.getTemperature() != null) {
            builder.temperature(agent.getTemperature().doubleValue());
            hasOptions = true;
        }
        return hasOptions ? builder.build() : null;
    }

    private String firstNotBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
