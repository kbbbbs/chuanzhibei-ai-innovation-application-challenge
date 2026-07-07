package com.cupk.aiacademy.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 聊天运行时配置。
 * <p>
 * 目前主要控制进入模型 Prompt 的历史、知识库和工具上下文规模，避免长会话把过多消息发送给模型。
 * 如需覆盖默认值，可在本地配置中使用 agent.chat.history-message-limit
 * 和 agent.chat.history-max-characters。
 */
@Component
@ConfigurationProperties(prefix = "agent.chat")
public class AgentChatProperties {
    private int historyMessageLimit = 30;
    private int historyMaxCharacters = 12000;
    private int knowledgeContextMaxSnippets = 5;
    private int knowledgeContextMaxCharacters = 4000;
    private int toolContextMaxItems = 5;
    private int toolContextMaxCharacters = 4000;

    public int getHistoryMessageLimit() {
        return Math.max(0, historyMessageLimit);
    }

    public void setHistoryMessageLimit(int historyMessageLimit) {
        this.historyMessageLimit = historyMessageLimit;
    }

    public int getHistoryMaxCharacters() {
        return Math.max(0, historyMaxCharacters);
    }

    public void setHistoryMaxCharacters(int historyMaxCharacters) {
        this.historyMaxCharacters = historyMaxCharacters;
    }

    public int getKnowledgeContextMaxSnippets() {
        return Math.max(0, knowledgeContextMaxSnippets);
    }

    public void setKnowledgeContextMaxSnippets(int knowledgeContextMaxSnippets) {
        this.knowledgeContextMaxSnippets = knowledgeContextMaxSnippets;
    }

    public int getKnowledgeContextMaxCharacters() {
        return Math.max(0, knowledgeContextMaxCharacters);
    }

    public void setKnowledgeContextMaxCharacters(int knowledgeContextMaxCharacters) {
        this.knowledgeContextMaxCharacters = knowledgeContextMaxCharacters;
    }

    public int getToolContextMaxItems() {
        return Math.max(0, toolContextMaxItems);
    }

    public void setToolContextMaxItems(int toolContextMaxItems) {
        this.toolContextMaxItems = toolContextMaxItems;
    }

    public int getToolContextMaxCharacters() {
        return Math.max(0, toolContextMaxCharacters);
    }

    public void setToolContextMaxCharacters(int toolContextMaxCharacters) {
        this.toolContextMaxCharacters = toolContextMaxCharacters;
    }
}
