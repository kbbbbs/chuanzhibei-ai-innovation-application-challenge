package com.cupk.aiacademy.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 聊天运行时配置。
 * <p>
 * 目前主要控制进入模型 Prompt 的历史上下文规模，避免长会话把过多消息发送给模型。
 * 如需覆盖默认值，可在本地配置中使用 agent.chat.history-message-limit
 * 和 agent.chat.history-max-characters。
 */
@Component
@ConfigurationProperties(prefix = "agent.chat")
public class AgentChatProperties {
    private int historyMessageLimit = 30;
    private int historyMaxCharacters = 12000;

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
}
