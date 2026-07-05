package com.cupk.aiacademy.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.config.AgentChatProperties;
import com.cupk.aiacademy.agent.exception.AgentModelCallException;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.dto.AgentChatResponse;
import com.cupk.aiacademy.dto.AgentMessageResponse;
import com.cupk.aiacademy.dto.AgentSessionCreateRequest;
import com.cupk.aiacademy.dto.AgentSessionUpdateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.mapper.AgentMessageMapper;
import com.cupk.aiacademy.mapper.AgentSessionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.pojo.AgentMessage;
import com.cupk.aiacademy.pojo.AgentSession;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class AgentChatService {
    private static final String SYSTEM_PROMPT = "你是一个智能教学管理平台助手";
    private static final int DEFAULT_AGENT_ID = 1;
    private static final int ACTIVE_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final String MODEL_FAILURE_CONTENT = "AI 回复失败，请稍后重试。";
    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;

    private final OpenAiChatModel chatModel;
    private final AgentChatProperties chatProperties;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final Executor streamExecutor;
    private final ExecutorService ownedStreamExecutor;

    @Autowired
    public AgentChatService(OpenAiChatModel chatModel,
                            AgentChatProperties chatProperties,
                            AgentDefinitionMapper agentDefinitionMapper,
                            AgentSessionMapper sessionMapper,
                            AgentMessageMapper messageMapper) {
        this(chatModel, chatProperties, agentDefinitionMapper, sessionMapper, messageMapper, Executors.newCachedThreadPool());
    }

    AgentChatService(OpenAiChatModel chatModel,
                     AgentChatProperties chatProperties,
                     AgentDefinitionMapper agentDefinitionMapper,
                     AgentSessionMapper sessionMapper,
                     AgentMessageMapper messageMapper,
                     Executor streamExecutor) {
        this.chatModel = chatModel;
        this.chatProperties = chatProperties == null ? new AgentChatProperties() : chatProperties;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.streamExecutor = streamExecutor;
        this.ownedStreamExecutor = streamExecutor instanceof ExecutorService executorService ? executorService : null;
    }

    @PreDestroy
    public void shutdownStreamExecutor() {
        if (ownedStreamExecutor != null) {
            ownedStreamExecutor.shutdown();
        }
    }

    //处理创建对话请求
    @Transactional
    public AgentSession createSession(AgentSessionCreateRequest request) {
        Integer agentId = request != null && request.getAgentId() != null ? request.getAgentId() : DEFAULT_AGENT_ID;
        ensureAgentDefinition(agentId);

        AgentSession session = new AgentSession();
        session.setAgentId(agentId);
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

    /**
     * 修改当前用户自己的 Agent 会话标题。
     */
    @Transactional
    public AgentSession updateSession(Integer sessionId, AgentSessionUpdateRequest request) {
        if (request == null || isBlank(request.getTitle())) {
            throw new BusinessException("会话标题不能为空");
        }
        AgentSession session = ensureSession(sessionId);
        session.setTitle(request.getTitle().trim());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return session;
    }

    /**
     * 软删除当前用户自己的 Agent 会话，保留消息用于审计或后续恢复。
     */
    @Transactional
    public void deleteSession(Integer sessionId) {
        AgentSession session = ensureSession(sessionId);
        session.setStatus(DELETED_STATUS);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
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
    @Transactional(noRollbackFor = AgentModelCallException.class)
    public AgentChatResponse chat(Integer sessionId, AgentChatRequest request) {
        if (request == null || isBlank(request.getContent())) {
            throw new BusinessException("消息内容不能为空");
        }
        AgentSession session = ensureSession(sessionId);//异常校验，确保会话存在
        AgentDefinition agent = ensureAgentDefinition(session.getAgentId());
        String content = request.getContent().trim();//去除消息内容两端空格
        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        ChatResponse chatResponse;
        try {
            chatResponse = chatModel.call(prompt);
        } catch (Exception e) {
            throw recordModelFailure(sessionId, userMessage.getId(), e);
        }

        String assistantContent = assistantContent(chatResponse);
        if (isBlank(assistantContent)) {
            throw recordModelFailure(sessionId, userMessage.getId(), null);
        }

        AgentMessage assistantMessage = setMessage(sessionId, "assistant", assistantContent);
        touchSession(sessionId);

        return toChatResponse(sessionId, userMessage.getId(), assistantMessage);
    }

    /**
     * 使用 SSE 推送模型流式响应。
     * <p>
     * 事件约定：
     * message  - 模型增量文本；
     * complete - 完整回复和消息 ID；
     * error    - 模型失败时的 Result 错误结构。
     */
    public SseEmitter streamChat(Integer sessionId, AgentChatRequest request) {
        if (request == null || isBlank(request.getContent())) {
            throw new BusinessException("消息内容不能为空");
        }
        AgentSession session = ensureSession(sessionId);
        AgentDefinition agent = ensureAgentDefinition(session.getAgentId());
        String content = request.getContent().trim();
        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor.execute(() -> streamModelReply(emitter, sessionId, userMessage.getId(), prompt));
        return emitter;
    }

    public String chatOnce(String content) {
        if (isBlank(content)) {
            throw new BusinessException("消息内容不能为空");
        }

        List<Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(content.trim())
        );
        ChatResponse chatResponse;
        try {
            chatResponse = chatModel.call(new Prompt(messages));
        } catch (Exception e) {
            throw new AgentModelCallException(MODEL_FAILURE_CONTENT, null, null, null, e);
        }
        String assistantContent = assistantContent(chatResponse);
        if (isBlank(assistantContent)) {
            throw new AgentModelCallException(MODEL_FAILURE_CONTENT, null, null, null);
        }
        return assistantContent;
    }

    private void streamModelReply(SseEmitter emitter, Integer sessionId, Integer userMessageId, Prompt prompt) {
        StringBuilder assistantContent = new StringBuilder();
        try {
            chatModel.stream(prompt)
                    .doOnNext(chatResponse -> sendStreamChunk(emitter, assistantContent, assistantContent(chatResponse)))
                    .blockLast();

            if (isBlank(assistantContent.toString())) {
                throw new AgentModelCallException(MODEL_FAILURE_CONTENT, sessionId, userMessageId, null);
            }

            AgentMessage assistantMessage = setMessage(sessionId, "assistant", assistantContent.toString());
            touchSession(sessionId);
            emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(toChatResponse(sessionId, userMessageId, assistantMessage), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            if (isStreamSendFailure(e)) {
                emitter.completeWithError(e);
                return;
            }
            AgentModelCallException modelException = e instanceof AgentModelCallException agentException
                    ? recordModelFailure(sessionId, userMessageId, agentException)
                    : recordModelFailure(sessionId, userMessageId, e);
            sendStreamError(emitter, modelException);
        }
    }

    private void sendStreamChunk(SseEmitter emitter, StringBuilder assistantContent, String chunk) {
        if (isBlank(chunk)) {
            return;
        }
        assistantContent.append(chunk);
        try {
            emitter.send(SseEmitter.event().name("message").data(chunk, MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 推送失败", e);
        }
    }

    private void sendStreamError(SseEmitter emitter, AgentModelCallException e) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Result.error("500", e.getMessage(), modelFailureData(e)), MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
            emitter.completeWithError(ignored);
            return;
        }
        emitter.complete();
    }

    private boolean isStreamSendFailure(Exception e) {
        return e instanceof IllegalStateException && "SSE 推送失败".equals(e.getMessage());
    }

    /**
     * 记录模型调用失败的助手消息，再抛出异常，让事务保留这次失败轨迹。
     */
    private AgentModelCallException recordModelFailure(Integer sessionId, Integer userMessageId, Exception cause) {
        AgentMessage assistantMessage = setMessage(sessionId, "assistant", MODEL_FAILURE_CONTENT);
        touchSession(sessionId);
        return new AgentModelCallException(MODEL_FAILURE_CONTENT, sessionId, userMessageId, assistantMessage.getId(), cause);
    }

    private AgentChatResponse toChatResponse(Integer sessionId, Integer userMessageId, AgentMessage assistantMessage) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId);
        response.setUserMessageId(userMessageId);
        response.setAssistantMessageId(assistantMessage.getId());
        response.setContent(assistantMessage.getContent());
        response.setCreateTime(assistantMessage.getCreateTime());
        return response;
    }

    private Map<String, Object> modelFailureData(AgentModelCallException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", e.getSessionId());
        data.put("userMessageId", e.getUserMessageId());
        data.put("assistantMessageId", e.getAssistantMessageId());
        return data;
    }

    private String assistantContent(ChatResponse chatResponse) {
        if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
            return chatResponse.getResult().getOutput().getContent();
        }
        return "";
    }

    /**
     * 按会话绑定的 Agent 定义构造 Prompt，使 system prompt、模型和温度配置真正生效。
     */
    private Prompt buildPrompt(AgentDefinition agent,
                               List<AgentMessage> historyMessages,
                               String currentUserContent) {
        List<Message> messages = buildPromptMessages(historyMessages, systemPrompt(agent), currentUserContent);
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

    /**
     * 从最近的历史消息开始选择上下文，并同时受消息条数和字符预算限制。
     */
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

    private AgentDefinition ensureAgentDefinition(Integer agentId) {
        if (agentId == null) {
            throw new BusinessException("agentId 不能为空");
        }
        AgentDefinition agent = agentDefinitionMapper.selectById(agentId);
        if (agent == null || !Integer.valueOf(ACTIVE_STATUS).equals(agent.getStatus())) {
            throw new BusinessException("Agent 不存在或已停用");
        }
        return agent;
    }

    private String systemPrompt(AgentDefinition agent) {
        return firstNotBlank(agent.getSystemPrompt(), SYSTEM_PROMPT);
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

    private AgentSession ensureSession(Integer sessionId) {
        if (sessionId == null) {
            throw new BusinessException("sessionId 不能为空");
        }
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null || !Integer.valueOf(ACTIVE_STATUS).equals(session.getStatus())) {
            throw new BusinessException("Agent 会话不存在");
        }
        ensureSessionOwner(session);
        return session;
    }

    /**
     * 校验当前登录用户只能访问自己的 Agent 会话。
     * <p>
     * AuthInterceptor 会在正常 HTTP 请求中写入 UserContext。
     * 没有登录上下文时保留原有内部调用兼容行为，由上层认证拦截器负责兜底。
     */
    private void ensureSessionOwner(AgentSession session) {
        Integer currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            return;
        }
        if (!currentUserId.equals(session.getUserId())) {
            throw new BusinessException("无权限访问该 Agent 会话");
        }
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
