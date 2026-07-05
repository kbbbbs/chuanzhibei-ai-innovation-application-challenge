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
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentChatService {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final String SYSTEM_PROMPT = "你是一个智能教学管理平台助手";
    private static final String DEFAULT_TITLE = "新会话";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String SSE_SEND_FAILURE = "SSE 推送失败";
    private static final int DEFAULT_AGENT_ID = 1;
    private static final int ACTIVE_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final String MODEL_FAILURE_CONTENT = "AI 回复失败，请稍后重试。";
    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;
    private static final Set<String> KNOWLEDGE_TEXT_FIELDS = Set.of(
            "content", "text", "answer", "chunk", "snippet", "summary", "title",
            "pagecontent", "documentcontent", "data", "result", "results"
    );

    private final OpenAiChatModel chatModel;
    private final AgentChatProperties chatProperties;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final WeKnoraService weKnoraService;
    private final AgentToolService agentToolService;
    private final Executor streamExecutor;
    private final ExecutorService ownedStreamExecutor;

    @Autowired
    public AgentChatService(OpenAiChatModel chatModel,
                            AgentChatProperties chatProperties,
                            AgentDefinitionMapper agentDefinitionMapper,
                            AgentSessionMapper sessionMapper,
                            AgentMessageMapper messageMapper,
                            ObjectMapper objectMapper,
                            WeKnoraService weKnoraService,
                            AgentToolService agentToolService
    ) {
        this(
                chatModel,
                chatProperties,
                agentDefinitionMapper,
                sessionMapper,
                messageMapper,
                Executors.newCachedThreadPool(),
                objectMapper,
                weKnoraService,
                agentToolService
        );
    }

    AgentChatService(OpenAiChatModel chatModel,
                     AgentChatProperties chatProperties,
                     AgentDefinitionMapper agentDefinitionMapper,
                     AgentSessionMapper sessionMapper,
                     AgentMessageMapper messageMapper,
                     Executor streamExecutor
    ) {
        this(
                chatModel,
                chatProperties,
                agentDefinitionMapper,
                sessionMapper,
                messageMapper,
                streamExecutor,
                new ObjectMapper(),
                null,
                null
        );
    }

    AgentChatService(OpenAiChatModel chatModel,
                     AgentChatProperties chatProperties,
                     AgentDefinitionMapper agentDefinitionMapper,
                     AgentSessionMapper sessionMapper,
                     AgentMessageMapper messageMapper,
                     Executor streamExecutor,
                     ObjectMapper objectMapper,
                     WeKnoraService weKnoraService,
                     AgentToolService agentToolService
    ) {
        this.chatModel = chatModel;
        this.chatProperties = chatProperties == null ? new AgentChatProperties() : chatProperties;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.weKnoraService = weKnoraService;
        this.agentToolService = agentToolService;
        this.streamExecutor = streamExecutor;
        this.ownedStreamExecutor = streamExecutor instanceof ExecutorService executorService ? executorService : null;
    }

    @PreDestroy
    public void shutdownStreamExecutor() {
        if (ownedStreamExecutor != null) {
            ownedStreamExecutor.shutdown();
        }
    }

    @Transactional
    public AgentSession createSession(AgentSessionCreateRequest request) {
        Integer agentId = request != null && request.getAgentId() != null ? request.getAgentId() : DEFAULT_AGENT_ID;
        ensureAgentDefinition(agentId);

        AgentSession session = new AgentSession();
        session.setAgentId(agentId);
        session.setUserId(currentOrRequestedUserId(request));
        session.setTitle(firstNotBlank(request == null ? null : request.getTitle(), DEFAULT_TITLE));
        session.setStatus(ACTIVE_STATUS);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Transactional(readOnly = true)
    public List<AgentSession> listSessions(Integer userId) {
        QueryWrapper<AgentSession> query = new QueryWrapper<>();
        query.eq("status", ACTIVE_STATUS);

        Integer targetUserId = currentOrRequestedUserId(userId);
        if (targetUserId != null) {
            query.eq("user_id", targetUserId);
        }

        query.orderByDesc("update_time").orderByDesc("id");
        return sessionMapper.selectList(query);
    }

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

    @Transactional
    public void deleteSession(Integer sessionId) {
        AgentSession session = ensureSession(sessionId);
        session.setStatus(DELETED_STATUS);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    @Transactional(readOnly = true)
    public List<AgentMessageResponse> listMessages(Integer sessionId) {
        ensureSession(sessionId);
        return loadMessages(sessionId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(noRollbackFor = AgentModelCallException.class)
    public AgentChatResponse chat(Integer sessionId, AgentChatRequest request) {
        String content = requireMessageContent(request);
        AgentSession session = ensureSession(sessionId);
        AgentDefinition agent = ensureAgentDefinition(session.getAgentId());

        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, ROLE_USER, content);
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

        AgentMessage assistantMessage = setMessage(sessionId, ROLE_ASSISTANT, assistantContent);
        touchSession(sessionId);

        return toChatResponse(sessionId, userMessage.getId(), assistantMessage);
    }

    public SseEmitter streamChat(Integer sessionId, AgentChatRequest request) {
        String content = requireMessageContent(request);
        AgentSession session = ensureSession(sessionId);
        AgentDefinition agent = ensureAgentDefinition(session.getAgentId());
        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, ROLE_USER, content);
        ensureSessionTitle(session, content);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor.execute(() -> streamModelReply(emitter, sessionId, userMessage.getId(), prompt));
        return emitter;
    }

    public String chatOnce(String content) {
        String trimmedContent = requireText(content, "消息内容不能为空");

        List<Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(trimmedContent)
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

            completeStream(emitter, sessionId, userMessageId, assistantContent.toString());
        } catch (Exception e) {
            if (isStreamSendFailure(e)) {
                emitter.completeWithError(e);
                return;
            }

            sendStreamError(emitter, recordModelFailure(sessionId, userMessageId, e));
        }
    }

    private String requireMessageContent(AgentChatRequest request) {
        return requireText(request == null ? null : request.getContent(), "消息内容不能为空");
    }

    private String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private void completeStream(SseEmitter emitter,
                                Integer sessionId,
                                Integer userMessageId,
                                String content) throws IOException {
        AgentMessage assistantMessage = setMessage(sessionId, ROLE_ASSISTANT, content);
        touchSession(sessionId);
        emitter.send(SseEmitter.event()
                .name("complete")
                .data(toChatResponse(sessionId, userMessageId, assistantMessage), MediaType.APPLICATION_JSON));
        emitter.complete();
    }

    private void sendStreamChunk(SseEmitter emitter, StringBuilder assistantContent, String chunk) {
        if (isBlank(chunk)) {
            return;
        }
        assistantContent.append(chunk);
        try {
            emitter.send(SseEmitter.event().name("message").data(chunk, MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            throw new IllegalStateException(SSE_SEND_FAILURE, e);
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
        return e instanceof IllegalStateException && SSE_SEND_FAILURE.equals(e.getMessage());
    }

    private AgentModelCallException recordModelFailure(Integer sessionId, Integer userMessageId, Exception cause) {
        AgentMessage assistantMessage = setMessage(sessionId, ROLE_ASSISTANT, MODEL_FAILURE_CONTENT);
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

    private Prompt buildPrompt(AgentDefinition agent,
                               List<AgentMessage> historyMessages,
                               String currentUserContent) {
        String knowledgeContext = retrieveKnowledgeContext(agent, currentUserContent);
        String toolContext = retrieveToolContext(agent, currentUserContent);
        List<Message> messages = buildPromptMessages(historyMessages,
                systemPrompt(agent, knowledgeContext, toolContext), currentUserContent);

        OpenAiChatOptions options = chatOptions(agent);
        return options == null ? new Prompt(messages) : new Prompt(messages, options);
    }

    private List<Message> buildPromptMessages(List<AgentMessage> historyMessages,
                                              String systemPrompt,
                                              String currentUserContent) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt));

        for (AgentMessage message : selectContextMessages(historyMessages)) {
            if (ROLE_USER.equals(message.getRole())) {
                promptMessages.add(new UserMessage(message.getContent()));
            } else if (ROLE_ASSISTANT.equals(message.getRole())) {
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
        return ROLE_USER.equals(role) || ROLE_ASSISTANT.equals(role);
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

    private String systemPrompt(AgentDefinition agent, String knowledgeContext, String toolContext) {
        StringBuilder prompt = new StringBuilder(systemPrompt(agent));
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

    private String retrieveKnowledgeContext(AgentDefinition agent, String currentUserContent) {
        List<String> knowledgeBaseIds = parseJsonArrayStrings(agent.getKnowledgeBaseIds(), "knowledgeBaseIds");
        if (knowledgeBaseIds.isEmpty()
                || weKnoraService == null
                || chatProperties.getKnowledgeContextMaxSnippets() <= 0
                || chatProperties.getKnowledgeContextMaxCharacters() <= 0) {
            return "";
        }

        try {
            Result result = weKnoraService.search(new WeKnoraSearchRequest(knowledgeBaseIds, currentUserContent.trim()));
            if (result == null || !"200".equals(result.getCode()) || result.getData() == null) {
                return "";
            }
            return formatKnowledgeContext(result.getData());
        } catch (Exception e) {
            log.warn("Agent knowledge search failed, continue without knowledge context", e);
            return "";
        }
    }

    private String retrieveToolContext(AgentDefinition agent, String currentUserContent) {
        List<String> enabledTools = parseJsonArrayStrings(agent.getEnabledTools(), "enabledTools");
        if (enabledTools.isEmpty()
                || agentToolService == null
                || chatProperties.getToolContextMaxItems() <= 0
                || chatProperties.getToolContextMaxCharacters() <= 0) {
            return "";
        }

        try {
            return agentToolService.buildToolContext(enabledTools,
                    currentUserContent.trim(),
                    chatProperties.getToolContextMaxItems(),
                    chatProperties.getToolContextMaxCharacters());
        } catch (Exception e) {
            log.warn("Agent tool execution failed, continue without tool context", e);
            return "";
        }
    }

    private List<String> parseJsonArrayStrings(String value, String fieldName) {
        if (isBlank(value)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value.trim());
            if (!node.isArray()) {
                return List.of();
            }

            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String text = item.isNull() ? "" : item.asText();
                if (!isBlank(text)) {
                    values.add(text.trim());
                }
            }
            return values;
        } catch (JsonProcessingException e) {
            log.warn("Invalid {} stored in Agent definition: {}", fieldName, value);
            return List.of();
        }
    }

    private String formatKnowledgeContext(Object searchData) {
        JsonNode root = objectMapper.valueToTree(searchData);
        List<String> snippets = new ArrayList<>();
        collectKnowledgeSnippets(root, null, snippets);
        if (snippets.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int maxCharacters = chatProperties.getKnowledgeContextMaxCharacters();
        for (int i = 0; i < snippets.size() && builder.length() < maxCharacters; i++) {
            String separator = builder.length() > 0 ? "\n" : "";
            String item = "[" + (i + 1) + "] " + snippets.get(i);
            int remaining = maxCharacters - builder.length() - separator.length();
            if (remaining <= 0) {
                break;
            }
            if (item.length() > remaining) {
                item = item.substring(0, remaining);
            }
            builder.append(separator).append(item);
        }
        return builder.toString();
    }

    private void collectKnowledgeSnippets(JsonNode node, String fieldName, List<String> snippets) {
        if (node == null || node.isNull() || snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectKnowledgeSnippets(child, fieldName, snippets);
            }
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                collectKnowledgeSnippets(field.getValue(), field.getKey(), snippets);
                if (snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
                    break;
                }
            }
            return;
        }
        if (node.isValueNode() && shouldCollectKnowledgeField(fieldName)) {
            addKnowledgeSnippet(node.asText(), snippets);
        }
    }

    private boolean shouldCollectKnowledgeField(String fieldName) {
        if (fieldName == null) {
            return true;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return KNOWLEDGE_TEXT_FIELDS.contains(normalized);
    }

    private void addKnowledgeSnippet(String value, List<String> snippets) {
        String snippet = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (isBlank(snippet) || snippets.contains(snippet)) {
            return;
        }
        snippets.add(snippet);
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
        if (session == null || !DEFAULT_TITLE.equals(session.getTitle())) {
            return;
        }

        String title = content == null ? DEFAULT_TITLE : content.trim();
        if (title.length() > 24) {
            title = title.substring(0, 24);
        }
        session.setTitle(firstNotBlank(title, DEFAULT_TITLE));
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

    private Integer currentOrRequestedUserId(AgentSessionCreateRequest request) {
        return currentOrRequestedUserId(request == null ? null : request.getUserId());
    }

    private Integer currentOrRequestedUserId(Integer requestedUserId) {
        Integer currentUserId = UserContext.getCurrentUserId();
        return currentUserId == null ? requestedUserId : currentUserId;
    }

    private String firstNotBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
