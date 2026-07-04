package com.cupk.aiacademy.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.llm.LlmChatRequest;
import com.cupk.aiacademy.agent.llm.LlmClient;
import com.cupk.aiacademy.agent.llm.LlmMessage;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import com.cupk.aiacademy.dto.AgentSessionCreateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.mapper.AgentMessageMapper;
import com.cupk.aiacademy.mapper.AgentSessionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.pojo.AgentMessage;
import com.cupk.aiacademy.pojo.AgentSession;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {
    private static final int ACTIVE_STATUS = 1;
    private static final int HISTORY_LIMIT = 30;

    private final AgentDefinitionMapper agentMapper;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final LlmClient llmClient;

    public AgentService(AgentDefinitionMapper agentMapper,
                        AgentSessionMapper sessionMapper,
                        AgentMessageMapper messageMapper,
                        LlmClient llmClient) {
        this.agentMapper = agentMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.llmClient = llmClient;
    }

    public List<AgentDefinition> listAgents() {
        QueryWrapper<AgentDefinition> query = new QueryWrapper<>();
        query.orderByDesc("status").orderByDesc("id");
        return agentMapper.selectList(query);
    }

    public AgentDefinition getAgent(Integer id) {
        AgentDefinition agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException("Agent 不存在");
        }
        return agent;
    }

    public AgentDefinition createAgent(AgentCreateRequest request) {
        if (request == null || isBlank(request.getCode()) || isBlank(request.getName())) {
            throw new BusinessException("Agent code 和 name 不能为空");
        }
        AgentDefinition agent = new AgentDefinition();
        agent.setCode(request.getCode().trim());
        agent.setName(request.getName().trim());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(firstNotBlank(request.getSystemPrompt(), defaultSystemPrompt()));
        agent.setModel(blankToNull(request.getModel()));
        agent.setTemperature(request.getTemperature() == null ? new BigDecimal("0.7") : request.getTemperature());
        agent.setKnowledgeBaseIds(request.getKnowledgeBaseIds());
        agent.setEnabledTools(request.getEnabledTools());
        agent.setStatus(request.getStatus() == null ? ACTIVE_STATUS : request.getStatus());
        agent.setCreateTime(LocalDateTime.now());
        agent.setUpdateTime(LocalDateTime.now());
        agentMapper.insert(agent);
        return agent;
    }

    public AgentSession createSession(AgentSessionCreateRequest request, Integer currentUserId) {
        if (request == null || request.getAgentId() == null) {
            throw new BusinessException("agentId 不能为空");
        }
        AgentDefinition agent = getAgent(request.getAgentId());
        if (!isActive(agent)) {
            throw new BusinessException("Agent 已停用");
        }

        AgentSession session = new AgentSession();
        session.setAgentId(agent.getId());
        session.setUserId(currentUserId == null ? request.getUserId() : currentUserId);
        session.setTitle(firstNotBlank(request.getTitle(), "新会话"));
        session.setStatus(ACTIVE_STATUS);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    public List<AgentSession> listSessions(Integer userId) {
        QueryWrapper<AgentSession> query = new QueryWrapper<>();
        if (userId != null) {
            query.eq("user_id", userId);
        }
        query.orderByDesc("update_time").orderByDesc("id");
        return sessionMapper.selectList(query);
    }

    public List<AgentMessage> listMessages(Integer sessionId) {
        ensureSession(sessionId);
        return loadMessages(sessionId);
    }

    public Map<String, Object> chat(Integer sessionId, AgentChatRequest request) throws Exception {
        if (request == null || isBlank(request.getContent())) {
            throw new BusinessException("消息内容不能为空");
        }
        AgentSession session = ensureSession(sessionId);
        AgentDefinition agent = getAgent(session.getAgentId());
        if (!isActive(agent)) {
            throw new BusinessException("Agent 已停用");
        }

        AgentMessage userMessage = saveMessage(sessionId, "user", request.getContent().trim());
        ensureSessionTitle(session, request.getContent());

        LlmChatRequest llmRequest = buildLlmRequest(agent, sessionId);
        String assistantContent = llmClient.chat(llmRequest);
        AgentMessage assistantMessage = saveMessage(sessionId, "assistant", assistantContent);
        touchSession(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("userMessage", userMessage);
        result.put("assistantMessage", assistantMessage);
        return result;
    }

    public SseEmitter streamChat(Integer sessionId, AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            StringBuilder assistantContent = new StringBuilder();
            try {
                if (request == null || isBlank(request.getContent())) {
                    throw new BusinessException("消息内容不能为空");
                }
                AgentSession session = ensureSession(sessionId);
                AgentDefinition agent = getAgent(session.getAgentId());
                if (!isActive(agent)) {
                    throw new BusinessException("Agent 已停用");
                }

                saveMessage(sessionId, "user", request.getContent().trim());
                ensureSessionTitle(session, request.getContent());

                LlmChatRequest llmRequest = buildLlmRequest(agent, sessionId);
                llmClient.streamChat(llmRequest, delta -> {
                    assistantContent.append(delta);
                    emitter.send(SseEmitter.event().name("message").data(delta));
                });

                AgentMessage assistantMessage = saveMessage(sessionId, "assistant", assistantContent.toString());
                touchSession(sessionId);
                emitter.send(SseEmitter.event().name("done").data(assistantMessage));
                emitter.complete();
            } catch (Exception e) {
                sendError(emitter, e);
            }
        }).start();

        return emitter;
    }

    private LlmChatRequest buildLlmRequest(AgentDefinition agent, Integer sessionId) {
        LlmChatRequest request = new LlmChatRequest();
        request.setModel(blankToNull(agent.getModel()));
        request.setTemperature(agent.getTemperature() == null ? null : agent.getTemperature().doubleValue());
        request.getMessages().add(new LlmMessage("system", firstNotBlank(agent.getSystemPrompt(), defaultSystemPrompt())));

        List<AgentMessage> messages = loadMessages(sessionId);
        int start = Math.max(0, messages.size() - HISTORY_LIMIT);
        for (AgentMessage message : messages.subList(start, messages.size())) {
            if (isBlank(message.getContent())) {
                continue;
            }
            if ("user".equals(message.getRole()) || "assistant".equals(message.getRole())) {
                request.getMessages().add(new LlmMessage(message.getRole(), message.getContent()));
            }
        }
        return request;
    }

    private AgentSession ensureSession(Integer sessionId) {
        if (sessionId == null) {
            throw new BusinessException("sessionId 不能为空");
        }
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("Agent 会话不存在");
        }
        return session;
    }

    private List<AgentMessage> loadMessages(Integer sessionId) {
        QueryWrapper<AgentMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByAsc("id");
        return messageMapper.selectList(query);
    }

    private AgentMessage saveMessage(Integer sessionId, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    private void ensureSessionTitle(AgentSession session, String content) {
        if (session == null || !"新会话".equals(session.getTitle())) {
            return;
        }
        String title = content == null ? "新会话" : content.trim();
        if (title.length() > 24) {
            title = title.substring(0, 24);
        }
        session.setTitle(isBlank(title) ? "新会话" : title);
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

    private boolean isActive(AgentDefinition agent) {
        return agent != null && (agent.getStatus() == null || agent.getStatus() == ACTIVE_STATUS);
    }

    private void sendError(SseEmitter emitter, Exception e) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("error", e.getMessage())));
        } catch (IOException ignored) {
        }
        emitter.completeWithError(e);
    }

    private String defaultSystemPrompt() {
        return "你是 AI 智学平台里的学习助教。请用清晰、准确、友好的中文回答，必要时分步骤说明。";
    }

    private String firstNotBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
