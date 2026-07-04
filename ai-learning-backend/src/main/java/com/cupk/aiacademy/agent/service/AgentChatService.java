package com.cupk.aiacademy.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.dto.AgentChatResponse;
import com.cupk.aiacademy.dto.AgentMessageResponse;
import com.cupk.aiacademy.dto.AgentSessionCreateRequest;
import com.cupk.aiacademy.mapper.AgentMessageMapper;
import com.cupk.aiacademy.mapper.AgentSessionMapper;
import com.cupk.aiacademy.pojo.AgentMessage;
import com.cupk.aiacademy.pojo.AgentSession;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;


@Service
public class AgentChatService {
    private static final String SYSTEM_PROMPT = "你是一个智能教学管理平台助手";
    private static final int DEFAULT_AGENT_ID = 1;
    private static final int ACTIVE_STATUS = 1;
    private static final int HISTORY_LIMIT = 30;

    private final OpenAiChatModel chatModel;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;

    public AgentChatService(OpenAiChatModel chatModel,
                            AgentSessionMapper sessionMapper,
                            AgentMessageMapper messageMapper) {
        this.chatModel = chatModel;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    //处理创建对话请求
    @Transactional
    public AgentSession createSession(AgentSessionCreateRequest request) {
        AgentSession session = new AgentSession();
        session.setAgentId(request != null && request.getAgentId() != null ? request.getAgentId() : DEFAULT_AGENT_ID);
        session.setUserId(currentUserId(request));
        session.setTitle(firstNotBlank(request == null ? null : request.getTitle(), "新会话"));
        session.setStatus(ACTIVE_STATUS);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    //列出userID用户的所有对话
    @Transactional(readOnly = true)
    public List<AgentSession> listSessions(Integer userId) {
        QueryWrapper<AgentSession> query = new QueryWrapper<>();
        query.eq("status", ACTIVE_STATUS);

        Integer currentUserId = UserContext.getCurrentUserId();
        Integer targetUserId = currentUserId == null ? userId : currentUserId;
        if (targetUserId != null) {
            query.eq("user_id", targetUserId);
        }

        query.orderByDesc("update_time").orderByDesc("id");
        return sessionMapper.selectList(query);
    }

    //列出对应对话ID的对话中的消息
    @Transactional(readOnly = true)
    public List<AgentMessageResponse> listMessages(Integer sessionId) {
        ensureSession(sessionId);
        return loadMessages(sessionId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    //进行对话
    @Transactional
    public AgentChatResponse chat(Integer sessionId, AgentChatRequest request) {
        if (request == null || isBlank(request.getContent())) {
            throw new BusinessException("消息内容不能为空");
        }
        AgentSession session = ensureSession(sessionId);//异常校验，确保会话存在
        String content = request.getContent().trim();//去除消息内容两端空格
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        ChatResponse chatResponse = chatModel.call(new Prompt(buildPromptMessages(sessionId)));
        String assistantContent = "";
        if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
            assistantContent = chatResponse.getResult().getOutput().getContent();
        }

        AgentMessage assistantMessage = setMessage(sessionId, "assistant", assistantContent);
        touchSession(sessionId);

        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId);
        response.setUserMessageId(userMessage.getId());
        response.setAssistantMessageId(assistantMessage.getId());
        response.setContent(assistantMessage.getContent());
        response.setCreateTime(assistantMessage.getCreateTime());
        return response;
    }

    public String chatOnce(String content) {
        if (isBlank(content)) {
            throw new BusinessException("消息内容不能为空");
        }

        List<Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(content.trim())
        );
        ChatResponse chatResponse = chatModel.call(new Prompt(messages));
        if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
            return chatResponse.getResult().getOutput().getContent();
        }
        return "";
    }

    private List<Message> buildPromptMessages(Integer sessionId) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(SYSTEM_PROMPT));

        List<AgentMessage> messages = loadMessages(sessionId);
        int start = Math.max(0, messages.size() - HISTORY_LIMIT);
        for (AgentMessage message : messages.subList(start, messages.size())) {
            if (isBlank(message.getContent())) {
                continue;
            }
            if ("user".equals(message.getRole())) {
                promptMessages.add(new UserMessage(message.getContent()));
            } else if ("assistant".equals(message.getRole())) {
                promptMessages.add(new AssistantMessage(message.getContent()));
            }
        }
        return promptMessages;
    }

    private AgentSession ensureSession(Integer sessionId) {
        if (sessionId == null) {
            throw new BusinessException("sessionId 不能为空");
        }
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null || !Integer.valueOf(ACTIVE_STATUS).equals(session.getStatus())) {
            throw new BusinessException("Agent 会话不存在");
        }
        return session;
    }

    private List<AgentMessage> loadMessages(Integer sessionId) {
        QueryWrapper<AgentMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByAsc("id");
        return messageMapper.selectList(query);
    }

    private AgentMessage setMessage(Integer sessionId, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    private AgentMessageResponse toMessageResponse(AgentMessage message) {
        AgentMessageResponse response = new AgentMessageResponse();
        response.setId(message.getId());
        response.setSessionId(message.getSessionId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setCreateTime(message.getCreateTime());
        return response;
    }

    private void ensureSessionTitle(AgentSession session, String content) {
        if (session == null || !"新会话".equals(session.getTitle())) {
            return;
        }

        String title = content == null ? "新会话" : content.trim();
        if (title.length() > 24) {
            title = title.substring(0, 24);
        }
        session.setTitle(firstNotBlank(title, "新会话"));
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private void touchSession(Integer sessionId) {
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    private Integer currentUserId(AgentSessionCreateRequest request) {
        Integer currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null) {
            return currentUserId;
        }
        return request == null ? null : request.getUserId();
    }

    private String firstNotBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
