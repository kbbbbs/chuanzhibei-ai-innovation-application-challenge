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
    public static final String CONCISE_RESPONSE_RULES = "回复要求：默认使用简洁、自然的中文，先直接回答结论。"
            + "简单问题控制在 1 到 4 句；需要列举时通常不超过 5 项。"
            + "不要复述用户问题，不写客套开场、重复总结或无关延伸。"
            + "只有用户明确要求详细解释、完整步骤、长文或教学展开时才增加篇幅。";

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
                promptMessages.add(new AssistantMessage(historyAssistantContent(message)));
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
        prompt.append("\n\n").append(CONCISE_RESPONSE_RULES);
        if (hasWritePlanTools(agent)) {
            prompt.append("\n\n系统操作真实性规则：普通聊天模型永远不能声称已经生成、创建、提交、执行或确认了系统操作，")
                    .append("也不能声称存在待确认动作、actionId 或确认ID。")
                    .append("读工具结果只代表完成了数据查询，绝不代表生成了写操作。")
                    .append("真实待确认动作只会由后端以结构化 pendingAction 返回，并且不会经过本普通聊天回答。")
                    .append("没有结构化写操作结果时，必须明确说明尚未生成或执行任何操作。");
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

    private String historyAssistantContent(AgentMessage message) {
        if ("pending_action".equals(message.getToolName())) {
            return "系统已接管该操作请求。";
        }
        if ("action_clarification".equals(message.getToolName())) {
            return "系统正在等待用户补充操作参数，尚未生成待确认动作。";
        }
        return message.getContent();
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
