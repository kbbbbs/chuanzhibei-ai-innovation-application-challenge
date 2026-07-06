package com.cupk.aiacademy.agent.core.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.agent.core.exception.AgentModelCallException;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.knowledge.KnowledgeContextProvider;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.api.AgentToolOperationType;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
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
    /**
     * Agent 聊天服务的基础配置常量：包含默认提示词、默认 Agent、状态值、失败兜底文案和流式响应超时时间。
     */
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final String SYSTEM_PROMPT = "你是一个智能教学管理平台助手";
    private static final int DEFAULT_AGENT_ID = 1;
    private static final int ACTIVE_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final String MODEL_FAILURE_CONTENT = "AI 回复失败，请稍后重试。";
    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;
    /**
     * 外部依赖：模型调用、配置、数据库 Mapper、JSON 处理、知识库检索、工具调用和流式执行线程池。
     */
    private final ChatModelClient chatModelClient;
    private final AgentChatProperties chatProperties;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final KnowledgeContextProvider knowledgeContextProvider;
    private final AgentToolExecutor agentToolExecutor;
    private final Executor streamExecutor;
    private final ExecutorService ownedStreamExecutor;

    /**
     * Spring 注入使用的构造方法，默认创建一个缓存线程池承载 SSE 流式响应任务。
     */
    @Autowired
    public AgentChatService(ChatModelClient chatModelClient,
                            AgentChatProperties chatProperties,
                            AgentDefinitionMapper agentDefinitionMapper,
                            AgentSessionMapper sessionMapper,
                            AgentMessageMapper messageMapper,
                            ObjectMapper objectMapper,
                            KnowledgeContextProvider knowledgeContextProvider,
                            AgentToolExecutor agentToolExecutor
    ) {
        this(
                chatModelClient,
                chatProperties,
                agentDefinitionMapper,
                sessionMapper,
                messageMapper,
                Executors.newCachedThreadPool(),
                objectMapper,
                knowledgeContextProvider,
                agentToolExecutor
        );
    }

    /**
     * 测试或内部构造使用的简化构造方法，允许调用方注入自定义执行器并跳过知识库、工具服务依赖。
     */
    AgentChatService(ChatModelClient chatModelClient,
                     AgentChatProperties chatProperties,
                     AgentDefinitionMapper agentDefinitionMapper,
                     AgentSessionMapper sessionMapper,
                     AgentMessageMapper messageMapper,
                     Executor streamExecutor
    ) {
        this(
                chatModelClient,
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

    /**
     * 统一初始化入口，负责设置默认配置、默认 ObjectMapper，并识别当前服务是否拥有线程池生命周期。
     */
    AgentChatService(ChatModelClient chatModelClient,
                     AgentChatProperties chatProperties,
                     AgentDefinitionMapper agentDefinitionMapper,
                     AgentSessionMapper sessionMapper,
                     AgentMessageMapper messageMapper,
                     Executor streamExecutor,
                     ObjectMapper objectMapper,
                     KnowledgeContextProvider knowledgeContextProvider,
                     AgentToolExecutor agentToolExecutor
    ) {
        this.chatModelClient = chatModelClient;
        this.chatProperties = chatProperties == null ? new AgentChatProperties() : chatProperties;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.knowledgeContextProvider = knowledgeContextProvider;
        this.agentToolExecutor = agentToolExecutor;
        this.streamExecutor = streamExecutor;
        this.ownedStreamExecutor = streamExecutor instanceof ExecutorService executorService ? executorService : null;
    }

    /**
     * Bean 销毁时关闭本服务创建的流式响应线程池，避免应用退出后线程仍然存活。
     */
    @PreDestroy
    public void shutdownStreamExecutor() {
        if (ownedStreamExecutor != null) {
            ownedStreamExecutor.shutdown();
        }
    }

    /**
     * 创建 Agent 会话：确定 Agent、绑定当前用户、初始化标题和状态，并写入数据库。
     */
    @Transactional
    public AgentSession createSession(AgentSessionCreateRequest request) {
        Integer agentId = request != null && request.getAgentId() != null ? request.getAgentId() : DEFAULT_AGENT_ID;
        ensureAgentDefinition(agentId);

        // 组装新会话的持久化字段，标题为空时使用“新会话”作为默认标题。
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

    /**
     * 查询当前用户的有效会话列表；存在登录上下文时优先使用当前登录用户，避免越权传入 userId。
     */
    @Transactional(readOnly = true)
    public List<AgentSession> listSessions(Integer userId) {
        QueryWrapper<AgentSession> query = new QueryWrapper<>();
        query.eq("status", ACTIVE_STATUS);

        // HTTP 请求中以 UserContext 为准；没有登录上下文时兼容内部调用传入的 userId。
        Integer currentUserId = UserContext.getCurrentUserId();
        Integer targetUserId = currentUserId == null ? userId : currentUserId;
        if (targetUserId != null) {
            query.eq("user_id", targetUserId);
        }

        query.orderByDesc("update_time").orderByDesc("id");
        return sessionMapper.selectList(query);
    }

    /**
     * 修改当前用户自己的 Agent 会话标题：先校验标题，再通过 ensureSession 完成存在性和归属校验。
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
     * 软删除 Agent 会话：不物理删除数据，只把状态改为已删除并刷新更新时间。
     */
    @Transactional
    public void deleteSession(Integer sessionId) {
        AgentSession session = ensureSession(sessionId);
        session.setStatus(DELETED_STATUS);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 查询指定会话的历史消息，并转换为接口响应对象。
     */
    @Transactional(readOnly = true)
    public List<AgentMessageResponse> listMessages(Integer sessionId) {
        ensureSession(sessionId);
        return loadMessages(sessionId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * 普通非流式聊天入口：保存用户消息、调用模型、保存助手回复，并返回本轮问答的消息 ID 和内容。
     */
    @Transactional(noRollbackFor = AgentModelCallException.class)
    public AgentChatResponse chat(Integer sessionId, AgentChatRequest request) {
        if (request == null || isBlank(request.getContent())) {
            throw new BusinessException("消息内容不能为空");
        }
        // 会话和 Agent 定义校验，确保请求只能进入有效且有权限访问的会话。
        AgentSession session = ensureSession(sessionId);
        AgentDefinition agent = ensureAgentDefinition(session.getAgentId());
        String content = request.getContent().trim();

        Optional<String> directReply = tryBuildDirectActionReply(agent, content);
        if (directReply.isPresent()) {
            AgentMessage userMessage = setMessage(sessionId, "user", content);
            ensureSessionTitle(session, content);
            AgentMessage assistantMessage = setMessage(sessionId, "assistant", directReply.get());
            touchSession(sessionId);
            return toChatResponse(sessionId, userMessage.getId(), assistantMessage);
        }

        // 构造包含系统提示词、知识库/工具上下文和历史消息的模型 Prompt，并先落库用户消息。
        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        // 调用模型失败时记录一条兜底助手消息，便于前端和历史记录展示失败轨迹。
        ChatResponse chatResponse;
        try {
            chatResponse = chatModelClient.call(prompt);
        } catch (Exception e) {
            throw recordModelFailure(sessionId, userMessage.getId(), e);
        }

        String assistantContent = assistantContent(chatResponse);
        if (isBlank(assistantContent)) {
            throw recordModelFailure(sessionId, userMessage.getId(), null);
        }

        // 模型成功返回后保存助手消息，刷新会话排序时间并组装响应。
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

        // 流式聊天在启动异步任务前完成校验、Prompt 构造和用户消息落库。
        AgentSession session = ensureSession(sessionId);
        AgentDefinition agent = ensureAgentDefinition(session.getAgentId());
        String content = request.getContent().trim();
        Optional<String> directReply = tryBuildDirectActionReply(agent, content);
        if (directReply.isPresent()) {
            AgentMessage userMessage = setMessage(sessionId, "user", content);
            ensureSessionTitle(session, content);
            return streamDirectReply(sessionId, userMessage.getId(), directReply.get());
        }

        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        // 返回 SseEmitter 给 Controller，同时在线程池中持续拉取模型增量回复。
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor.execute(() -> streamModelReply(emitter, sessionId, userMessage.getId(), prompt));
        return emitter;
    }

    private SseEmitter streamDirectReply(Integer sessionId, Integer userMessageId, String assistantContent) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor.execute(() -> {
            try {
                sendStreamChunk(emitter, new StringBuilder(), assistantContent);
                AgentMessage assistantMessage = setMessage(sessionId, "assistant", assistantContent);
                touchSession(sessionId);
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(toChatResponse(sessionId, userMessageId, assistantMessage), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 单轮无会话聊天入口：只使用默认系统提示词和当前用户消息，不保存历史记录。
     */
    public String chatOnce(String content) {
        if (isBlank(content)) {
            throw new BusinessException("消息内容不能为空");
        }

        // 构建最小 Prompt，适合不需要会话、知识库和工具上下文的轻量调用。
        List<Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(content.trim())
        );

        // 直接同步调用模型，并把空回复和调用异常统一转换为 AgentModelCallException。
        ChatResponse chatResponse;
        try {
            chatResponse = chatModelClient.call(new Prompt(messages));
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
            // 订阅模型流式输出：每个增量片段都会立即推送给前端，并累计成完整回复。
            chatModelClient.stream(prompt)
                    .doOnNext(chatResponse -> sendStreamChunk(emitter, assistantContent, assistantContent(chatResponse)))
                    .blockLast();

            // 流结束后仍没有有效内容，按模型失败处理并记录失败消息。
            if (isBlank(assistantContent.toString())) {
                throw new AgentModelCallException(MODEL_FAILURE_CONTENT, sessionId, userMessageId, null);
            }

            // 完整回复落库后发送 complete 事件，通知前端本轮流式对话已结束。
            AgentMessage assistantMessage = setMessage(sessionId, "assistant", assistantContent.toString());
            touchSession(sessionId);
            emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(toChatResponse(sessionId, userMessageId, assistantMessage), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            // 客户端断开或 SSE 写出失败时结束连接，不再额外记录模型失败。
            if (isStreamSendFailure(e)) {
                emitter.completeWithError(e);
                return;
            }

            // 模型侧异常统一记录兜底助手消息，并通过 error 事件返回结构化错误信息。
            AgentModelCallException modelException = e instanceof AgentModelCallException agentException
                    ? recordModelFailure(sessionId, userMessageId, agentException)
                    : recordModelFailure(sessionId, userMessageId, e);
            sendStreamError(emitter, modelException);
        }
    }

    /**
     * 推送单个 SSE 增量消息：过滤空片段，累计完整助手回复，并把本片段发送给前端。
     */
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

    /**
     * 推送 SSE 错误事件：把模型异常包装成统一 Result 结构后结束本次流式连接。
     */
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

    /**
     * 判断异常是否来自 SSE 写出失败，用于区分客户端连接问题和模型调用失败。
     */
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

    /**
     * 把一次聊天结果转换为接口响应对象，包含会话 ID、用户消息 ID、助手消息 ID、内容和创建时间。
     */
    private AgentChatResponse toChatResponse(Integer sessionId, Integer userMessageId, AgentMessage assistantMessage) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId);
        response.setUserMessageId(userMessageId);
        response.setAssistantMessageId(assistantMessage.getId());
        response.setContent(assistantMessage.getContent());
        response.setCreateTime(assistantMessage.getCreateTime());
        return response;
    }

    /**
     * 构造模型失败时返回给前端的扩展数据，方便前端定位本轮失败对应的消息记录。
     */
    private Map<String, Object> modelFailureData(AgentModelCallException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", e.getSessionId());
        data.put("userMessageId", e.getUserMessageId());
        data.put("assistantMessageId", e.getAssistantMessageId());
        return data;
    }

    /**
     * 从 Spring AI 的 ChatResponse 中安全提取助手回复正文，任意层级为空时返回空字符串。
     */
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
        // 先取知识库和工具上下文，再把它们合并进 system prompt，提升模型回答的业务依据。
        String knowledgeContext = retrieveKnowledgeContext(agent, currentUserContent);
        String toolContext = retrieveToolContext(agent, currentUserContent);
        List<Message> messages = buildPromptMessages(historyMessages,
                systemPrompt(agent, knowledgeContext, toolContext), currentUserContent);

        // Agent 定义中的模型名和温度配置存在时，作为本次 Prompt 的调用选项传给模型。
        OpenAiChatOptions options = chatOptions(agent);
        return options == null ? new Prompt(messages) : new Prompt(messages, options);
    }

    /**
     * 组装模型消息列表：system prompt 在最前，随后是筛选后的历史上下文，最后追加当前用户消息。
     */
    private List<Message> buildPromptMessages(List<AgentMessage> historyMessages,
                                              String systemPrompt,
                                              String currentUserContent) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt));

        // 数据库中的 user/assistant 角色要转换成 Spring AI 对应的消息类型。
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
        // 没有历史消息或配置不允许携带历史时，直接返回空上下文。
        if (messages == null || messages.isEmpty()
                || chatProperties.getHistoryMessageLimit() <= 0
                || chatProperties.getHistoryMaxCharacters() <= 0) {
            return List.of();
        }

        // 从最新消息向前挑选，直到达到条数限制或字符预算限制。
        List<AgentMessage> selected = new ArrayList<>();
        int usedCharacters = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            // 只保留可进入 Prompt 的 user/assistant 文本消息。
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
        // 选择时是倒序遍历，返回前恢复为原始对话顺序。
        Collections.reverse(selected);
        return selected;
    }

    /**
     * 判断消息角色是否能作为模型上下文，当前只允许用户消息和助手消息进入 Prompt。
     */
    private boolean isPromptRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    /**
     * 校验 Agent 定义是否存在且处于启用状态，并返回完整 Agent 配置。
     */
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

    /**
     * 读取 Agent 自定义系统提示词；未配置时使用平台默认提示词。
     */
    private String systemPrompt(AgentDefinition agent) {
        return firstNotBlank(agent.getSystemPrompt(), SYSTEM_PROMPT);
    }

    /**
     * 拼接最终系统提示词：基础角色设定后追加知识库资料和工具调用结果。
     */
    private String systemPrompt(AgentDefinition agent, String knowledgeContext, String toolContext) {
        StringBuilder prompt = new StringBuilder(systemPrompt(agent));
        // 知识库上下文用于补充课程、文档等检索资料，并约束模型说明资料不足的情况。
        if (!isBlank(knowledgeContext)) {
            prompt.append("\n\n知识库参考资料（优先基于资料回答；资料不足时说明无法从资料中确认）：\n")
                    .append(knowledgeContext);
        }
        // 工具上下文用于补充平台实时数据或业务查询结果。
        if (!isBlank(toolContext)) {
            prompt.append("\n\n工具调用结果（优先结合这些平台数据回答；数据不足时说明限制）：\n")
                    .append(toolContext);
        }
        return prompt.toString();
    }

    /**
     * 根据 Agent 绑定的知识库配置读取知识库上下文，供模型结合资料回答。
     */
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

    /**
     * 根据 Agent 启用的工具列表构建工具上下文，供模型结合平台业务数据回答。
     */
    private String retrieveToolContext(AgentDefinition agent, String currentUserContent) {
        List<String> enabledTools = parseEnabledTools(agent.getEnabledTools());
        // 未启用工具、服务未注入或上下文预算关闭时，跳过工具调用。
        if (enabledTools.isEmpty()
                || agentToolExecutor == null
                || chatProperties.getToolContextMaxItems() <= 0
                || chatProperties.getToolContextMaxCharacters() <= 0) {
            return "";
        }

        try {
            // 工具服务内部负责按工具类型执行查询，并限制条数和字符长度。
            return agentToolExecutor.buildToolContext(enabledTools,
                    currentUserContent.trim(),
                    chatProperties.getToolContextMaxItems(),
                    chatProperties.getToolContextMaxCharacters());
        } catch (Exception e) {
            // 工具调用失败不影响聊天主流程，只在日志中记录问题。
            log.warn("Agent tool execution failed, continue without tool context", e);
            return "";
        }
    }

    private Optional<String> tryBuildDirectActionReply(AgentDefinition agent, String currentUserContent) {
        if (agentToolExecutor == null || isBlank(currentUserContent)) {
            return Optional.empty();
        }
        List<String> enabledTools = parseEnabledTools(agent.getEnabledTools());
        Optional<String> planToolName = directPlanToolName(enabledTools, currentUserContent);
        if (planToolName.isEmpty()) {
            return Optional.empty();
        }
        try {
            return agentToolExecutor.executeTools(List.of(planToolName.get()),
                            currentUserContent.trim(),
                            1)
                    .stream()
                    .filter(result -> result.operationType() == AgentToolOperationType.WRITE_PLAN)
                    .findFirst()
                    .map(this::formatWritePlanReply);
        } catch (Exception e) {
            log.warn("Agent direct action routing failed, fallback to model chat", e);
            return Optional.empty();
        }
    }

    private Optional<String> directPlanToolName(List<String> enabledTools, String currentUserContent) {
        if (enabledTools.isEmpty()) {
            return Optional.empty();
        }
        if (isEnabledTool(enabledTools, "notice_create_plan", "notice_publish_plan", "announcement_create_plan")
                && looksLikeNoticeCreateRequest(currentUserContent)) {
            return Optional.of("notice_create_plan");
        }
        if (isEnabledTool(enabledTools, "course_resource_remove_plan", "lesson_resource_remove_plan",
                "course_resource_delete_plan", "lesson_resource_delete_plan")
                && looksLikeCourseResourceRemoveRequest(currentUserContent)) {
            return Optional.of("course_resource_remove_plan");
        }
        if (isEnabledTool(enabledTools, "course_resource_attach_plan", "lesson_resource_attach_plan",
                "course_resource_bind_plan", "lesson_resource_bind_plan")
                && looksLikeCourseResourceAttachRequest(currentUserContent)) {
            return Optional.of("course_resource_attach_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_status_plan", "course_lesson_status_plan",
                "lesson_archive_plan", "lesson_delete_plan", "lesson_remove_plan")
                && looksLikeLessonStatusRequest(currentUserContent)) {
            return Optional.of("lesson_status_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_sort_plan", "course_lesson_sort_plan", "lesson_reorder_plan")
                && looksLikeLessonSortRequest(currentUserContent)) {
            return Optional.of("lesson_sort_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_create_plan", "course_lesson_create_plan", "lesson_add_plan")
                && looksLikeLessonCreateRequest(currentUserContent)) {
            return Optional.of("lesson_create_plan");
        }
        if (isEnabledTool(enabledTools, "lesson_update_plan", "course_lesson_update_plan",
                "lesson_modify_plan", "lesson_edit_plan")
                && looksLikeLessonUpdateRequest(currentUserContent)) {
            return Optional.of("lesson_update_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_sort_plan", "course_chapter_sort_plan", "chapter_reorder_plan")
                && looksLikeChapterSortRequest(currentUserContent)) {
            return Optional.of("chapter_sort_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_delete_plan", "course_chapter_delete_plan",
                "chapter_remove_plan", "course_chapter_remove_plan")
                && looksLikeChapterDeleteRequest(currentUserContent)) {
            return Optional.of("chapter_delete_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_create_plan", "course_chapter_create_plan", "chapter_add_plan")
                && looksLikeChapterCreateRequest(currentUserContent)) {
            return Optional.of("chapter_create_plan");
        }
        if (isEnabledTool(enabledTools, "chapter_update_plan", "course_chapter_update_plan",
                "chapter_modify_plan", "chapter_edit_plan")
                && looksLikeChapterUpdateRequest(currentUserContent)) {
            return Optional.of("chapter_update_plan");
        }
        if (isEnabledTool(enabledTools, "class_student_remove_plan", "class_student_delete_plan",
                "teaching_class_student_remove_plan", "class_member_remove_plan", "student_unenroll_plan")
                && looksLikeClassStudentRemoveRequest(currentUserContent)) {
            return Optional.of("class_student_remove_plan");
        }
        if (isEnabledTool(enabledTools, "class_student_status_plan", "class_member_status_plan",
                "class_student_update_status_plan", "student_enrollment_status_plan")
                && looksLikeClassStudentStatusRequest(currentUserContent)) {
            return Optional.of("class_student_status_plan");
        }
        if (isEnabledTool(enabledTools, "class_student_add_plan", "teaching_class_student_add_plan",
                "class_member_add_plan", "student_enroll_plan")
                && looksLikeClassStudentAddRequest(currentUserContent)) {
            return Optional.of("class_student_add_plan");
        }
        if (isEnabledTool(enabledTools, "teaching_class_status_plan", "class_status_plan",
                "teaching_class_archive_plan", "teaching_class_delete_plan", "class_delete_plan")
                && looksLikeTeachingClassStatusRequest(currentUserContent)) {
            return Optional.of("teaching_class_status_plan");
        }
        if (isEnabledTool(enabledTools, "teaching_class_create_plan", "class_create_plan",
                "teaching_class_add_plan", "class_add_plan")
                && looksLikeTeachingClassCreateRequest(currentUserContent)) {
            return Optional.of("teaching_class_create_plan");
        }
        if (isEnabledTool(enabledTools, "teaching_class_update_plan", "class_update_plan",
                "teaching_class_modify_plan", "class_modify_plan", "teaching_class_edit_plan")
                && looksLikeTeachingClassUpdateRequest(currentUserContent)) {
            return Optional.of("teaching_class_update_plan");
        }
        if (isEnabledTool(enabledTools, "course_status_plan", "course_publish_status_plan",
                "course_archive_plan", "course_delete_plan", "course_remove_plan")
                && looksLikeCourseStatusRequest(currentUserContent)) {
            return Optional.of("course_status_plan");
        }
        if (isEnabledTool(enabledTools, "course_create_plan", "course_publish_plan", "course_add_plan")
                && looksLikeCourseCreateRequest(currentUserContent)) {
            return Optional.of("course_create_plan");
        }
        if (isEnabledTool(enabledTools, "course_update_plan", "course_modify_plan", "course_edit_plan")
                && looksLikeCourseUpdateRequest(currentUserContent)) {
            return Optional.of("course_update_plan");
        }
        if (isEnabledTool(enabledTools, "homework_submission_grade_plan", "homework_grade_plan",
                "homework_submit_grade_plan", "assignment_grade_plan")
                && looksLikeHomeworkSubmissionGradeRequest(currentUserContent)) {
            return Optional.of("homework_submission_grade_plan");
        }
        if (isEnabledTool(enabledTools, "homework_delete_plan", "homework_remove_plan",
                "assignment_delete_plan", "assignment_remove_plan")
                && looksLikeHomeworkDeleteRequest(currentUserContent)) {
            return Optional.of("homework_delete_plan");
        }
        if (isEnabledTool(enabledTools, "homework_create_plan", "homework_publish_plan", "assignment_create_plan")
                && looksLikeHomeworkCreateRequest(currentUserContent)) {
            return Optional.of("homework_create_plan");
        }
        if (isEnabledTool(enabledTools, "homework_update_plan", "homework_modify_plan",
                "assignment_update_plan", "assignment_modify_plan")
                && looksLikeHomeworkUpdateRequest(currentUserContent)) {
            return Optional.of("homework_update_plan");
        }
        if (isEnabledTool(enabledTools, "exam_answer_grade_plan", "exam_manual_grade_plan", "exam_submission_grade_plan",
                "exam_answer_score_plan")
                && looksLikeExamAnswerGradeRequest(currentUserContent)) {
            return Optional.of("exam_answer_grade_plan");
        }
        if (isEnabledTool(enabledTools, "exam_paper_question_add_plan", "paper_question_add_plan",
                "exam_paper_question_attach_plan", "paper_question_attach_plan")
                && looksLikeExamPaperQuestionAddRequest(currentUserContent)) {
            return Optional.of("exam_paper_question_add_plan");
        }
        if (isEnabledTool(enabledTools, "exam_paper_question_remove_plan", "paper_question_remove_plan",
                "exam_paper_question_delete_plan", "paper_question_delete_plan")
                && looksLikeExamPaperQuestionRemoveRequest(currentUserContent)) {
            return Optional.of("exam_paper_question_remove_plan");
        }
        if (isEnabledTool(enabledTools, "exam_paper_question_update_plan", "paper_question_update_plan",
                "exam_paper_question_modify_plan", "paper_question_modify_plan")
                && looksLikeExamPaperQuestionUpdateRequest(currentUserContent)) {
            return Optional.of("exam_paper_question_update_plan");
        }
        if (isEnabledTool(enabledTools, "exam_paper_create_plan", "exam_paper_add_plan",
                "paper_create_plan", "paper_add_plan")
                && looksLikeExamPaperCreateRequest(currentUserContent)) {
            return Optional.of("exam_paper_create_plan");
        }
        if (isEnabledTool(enabledTools, "exam_paper_delete_plan", "exam_paper_remove_plan",
                "paper_delete_plan", "paper_remove_plan")
                && looksLikeExamPaperDeleteRequest(currentUserContent)) {
            return Optional.of("exam_paper_delete_plan");
        }
        if (isEnabledTool(enabledTools, "exam_paper_update_plan", "exam_paper_modify_plan",
                "paper_update_plan", "paper_modify_plan")
                && looksLikeExamPaperUpdateRequest(currentUserContent)) {
            return Optional.of("exam_paper_update_plan");
        }
        if (isEnabledTool(enabledTools, "question_create_plan", "exam_question_create_plan",
                "question_add_plan", "question_bank_create_plan")
                && looksLikeQuestionCreateRequest(currentUserContent)) {
            return Optional.of("question_create_plan");
        }
        if (isEnabledTool(enabledTools, "question_update_plan", "exam_question_update_plan",
                "question_modify_plan", "question_edit_plan")
                && looksLikeQuestionUpdateRequest(currentUserContent)) {
            return Optional.of("question_update_plan");
        }
        if (isEnabledTool(enabledTools, "question_delete_plan", "exam_question_delete_plan",
                "question_remove_plan", "question_bank_delete_plan")
                && looksLikeQuestionDeleteRequest(currentUserContent)) {
            return Optional.of("question_delete_plan");
        }
        if (isEnabledTool(enabledTools, "grade_record_delete_plan", "grade_delete_plan", "score_delete_plan",
                "grade_remove_plan", "score_remove_plan")
                && looksLikeGradeRecordDeleteRequest(currentUserContent)) {
            return Optional.of("grade_record_delete_plan");
        }
        if (isEnabledTool(enabledTools, "grade_record_create_plan", "grade_create_plan", "score_create_plan",
                "grade_input_plan", "score_input_plan")
                && looksLikeGradeRecordCreateRequest(currentUserContent)) {
            return Optional.of("grade_record_create_plan");
        }
        if (isEnabledTool(enabledTools, "grade_record_update_plan", "grade_update_plan", "score_update_plan",
                "score_modify_plan", "grade_modify_plan")
                && looksLikeGradeRecordUpdateRequest(currentUserContent)) {
            return Optional.of("grade_record_update_plan");
        }
        if (isEnabledTool(enabledTools, "exam_session_close_plan", "exam_close_plan", "exam_end_plan",
                "exam_session_end_plan", "exam_session_finish_plan")
                && looksLikeExamSessionCloseRequest(currentUserContent)) {
            return Optional.of("exam_session_close_plan");
        }
        if (isEnabledTool(enabledTools, "exam_session_create_plan", "exam_create_plan", "exam_publish_plan", "exam_session_publish_plan")
                && looksLikeExamSessionCreateRequest(currentUserContent)) {
            return Optional.of("exam_session_create_plan");
        }
        return Optional.empty();
    }

    private String formatWritePlanReply(AgentToolResult result) {
        StringBuilder reply = new StringBuilder("已生成待确认操作计划。确认前请核对以下信息：\n");
        reply.append(result.content());
        Object actionId = result.data().get("actionId");
        if (actionId != null) {
            reply.append("\n确认执行时提交 actionId=").append(actionId).append("，并补齐 parameters。");
        }
        return reply.toString();
    }

    private boolean looksLikeNoticeCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "通知", "公告")
                && containsAny(value, "发布", "创建", "新建", "发送", "发一条", "发个");
    }

    private boolean looksLikeHomeworkCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "发布", "布置", "创建", "新建", "发送", "留一份", "留个");
    }

    private boolean looksLikeCourseCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "课程", "门课")
                && containsAny(value, "创建", "新建", "新增", "添加", "开设", "发布", "建一门", "加一门");
    }

    private boolean looksLikeLessonCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "创建", "新建", "新增", "添加", "加一个", "建一个", "上传");
    }

    private boolean looksLikeCourseResourceAttachRequest(String content) {
        String value = content == null ? "" : content.trim();
        return !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "课时", "小节", "课程资源", "资源")
                && containsAny(value, "资源", "视频", "文档", "资料", "链接", "课件", "附件")
                && containsAny(value, "挂载", "绑定", "关联", "附加", "上传", "添加", "设置", "替换", "换成");
    }

    private boolean looksLikeCourseResourceRemoveRequest(String content) {
        String value = content == null ? "" : content.trim();
        return !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "课时", "小节", "课程资源", "资源")
                && containsAny(value, "资源", "视频", "文档", "资料", "链接", "课件", "附件", "正文", "内容")
                && containsAny(value, "移除", "删除", "清空", "解绑", "取消绑定", "解除绑定", "去掉", "删掉", "删去", "撤掉");
    }

    private boolean looksLikeLessonUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名", "替换");
    }

    private boolean looksLikeLessonSortRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "排序", "顺序", "调整顺序", "调序", "重新排序", "排到", "移到", "移动");
    }

    private boolean looksLikeLessonStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeLessonTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "上架", "下架", "停用", "启用", "恢复", "归档", "关闭",
                "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeLessonTarget(String value) {
        if (value == null) {
            return false;
        }
        return containsAny(value, "课时", "小节", "课程内容", "视频课");
    }

    private boolean looksLikeChapterCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "创建", "新建", "新增", "添加", "加一章", "建一章");
    }

    private boolean looksLikeChapterUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名");
    }

    private boolean looksLikeChapterSortRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "排序", "顺序", "调整顺序", "调序", "重新排序", "排到", "移到", "移动");
    }

    private boolean looksLikeChapterDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeChapterTarget(value)
                && !containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeChapterTarget(String value) {
        if (value == null) {
            return false;
        }
        return value.contains("章节") || (value.contains("课程") && value.contains("章"));
    }

    private boolean looksLikeTeachingClassCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeTeachingClassTarget(value)
                && containsAny(value, "创建", "新建", "新增", "添加", "开设", "建班", "建一个");
    }

    private boolean looksLikeTeachingClassUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeTeachingClassTarget(value)
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名");
    }

    private boolean looksLikeTeachingClassStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeTeachingClassTarget(value)
                && containsAny(value, "结束", "关闭", "停用", "启用", "恢复", "归档", "删除",
                "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeTeachingClassTarget(String value) {
        if (value == null) {
            return false;
        }
        return containsAny(value, "教学班", "班级", "授课班", "课程班");
    }

    private boolean looksLikeClassStudentAddRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeClassStudentTarget(value)
                && containsAny(value, "加入", "添加", "新增", "录入", "纳入", "加到", "加进");
    }

    private boolean looksLikeClassStudentRemoveRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeClassStudentTarget(value)
                && containsAny(value, "移除", "删除", "移出", "踢出", "退班", "删掉", "删去");
    }

    private boolean looksLikeClassStudentStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return looksLikeClassStudentTarget(value)
                && containsAny(value, "休学", "恢复", "正常", "停用", "启用", "调整状态", "变更状态",
                "状态改为", "状态改成");
    }

    private boolean looksLikeClassStudentTarget(String value) {
        if (value == null) {
            return false;
        }
        return containsAny(value, "班级学生", "教学班学生", "班级成员", "学生名单")
                || (containsAny(value, "教学班", "班级", "授课班", "课程班")
                && containsAny(value, "学生", "成员"));
    }

    private boolean looksLikeCourseUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "课程", "门课")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "改名");
    }

    private boolean looksLikeCourseStatusRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "课程", "门课")
                && containsAny(value, "上架", "下架", "停用", "启用", "恢复", "归档", "关闭",
                "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeHomeworkDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeHomeworkUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务")
                && containsAny(value, "修改", "调整", "更改", "更新", "改成", "改为", "改到", "延后", "提前");
    }

    private boolean looksLikeHomeworkSubmissionGradeRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "作业", "练习", "任务", "提交")
                && containsAny(value, "批改", "评分", "打分", "给分", "判分", "评语", "反馈");
    }

    private boolean looksLikeExamSessionCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "考试", "测验", "考核")
                && containsAny(value, "创建", "新建", "安排", "发布", "开启", "开一场");
    }

    private boolean looksLikeExamPaperCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "试卷", "卷子", "考卷", "测试卷")
                && containsAny(value, "创建", "新建", "新增", "添加", "生成", "建一套", "出一套");
    }

    private boolean looksLikeExamPaperUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "试卷", "卷子", "考卷", "测试卷")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "设置");
    }

    private boolean looksLikeExamPaperQuestionAddRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "试卷", "卷子", "考卷", "测试卷", "组卷")
                && containsAny(value, "题目", "试题", "考题", "题", "组卷")
                && containsAny(value, "添加", "新增", "加入", "加到", "放到", "挂到", "组卷", "编入");
    }

    private boolean looksLikeExamPaperQuestionRemoveRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "试卷", "卷子", "考卷", "测试卷")
                && containsAny(value, "题目", "试题", "考题", "题")
                && containsAny(value, "删除", "移除", "撤下", "去掉", "删掉", "删去", "移出", "拿掉");
    }

    private boolean looksLikeExamPaperQuestionUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "试卷", "卷子", "考卷", "测试卷")
                && containsAny(value, "题目", "试题", "考题", "题", "分值", "分数", "顺序", "排序", "必答", "选答")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "改成", "改为", "设置", "排到", "调到");
    }

    private boolean looksLikeExamPaperDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "试卷", "卷子", "考卷", "测试卷")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去", "归档");
    }

    private boolean looksLikeQuestionCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "题目", "试题", "题库", "考题", "单选题", "多选题", "判断题", "填空题", "简答题")
                && containsAny(value, "创建", "新建", "新增", "添加", "录入", "出一道", "加一道");
    }

    private boolean looksLikeQuestionUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "题目", "试题", "题库", "考题", "单选题", "多选题", "判断题", "填空题", "简答题")
                && containsAny(value, "修改", "调整", "更改", "更新", "编辑", "修正", "改成", "改为", "设置");
    }

    private boolean looksLikeQuestionDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "题目", "试题", "题库", "考题", "单选题", "多选题", "判断题", "填空题", "简答题")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeGradeRecordUpdateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "成绩", "分数", "得分", "分值")
                && containsAny(value, "修改", "调整", "更正", "改成", "改为", "更新", "记为", "设为");
    }

    private boolean looksLikeGradeRecordCreateRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "成绩", "分数", "得分", "分值")
                && containsAny(value, "录入", "登记", "新增", "添加", "创建", "加一条", "记一条");
    }

    private boolean looksLikeGradeRecordDeleteRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "成绩", "分数", "得分", "分值")
                && containsAny(value, "删除", "移除", "撤销", "作废", "删掉", "删去");
    }

    private boolean looksLikeExamAnswerGradeRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "考试", "答卷", "答题", "主观题", "试题")
                && containsAny(value, "批改", "评分", "打分", "给分", "判分", "阅卷", "复核");
    }

    private boolean looksLikeExamSessionCloseRequest(String content) {
        String value = content == null ? "" : content.trim();
        return containsAny(value, "考试", "测验", "考核")
                && containsAny(value, "关闭", "结束", "终止", "停止", "收卷");
    }

    private boolean isEnabledTool(List<String> enabledTools, String... names) {
        Set<String> normalizedEnabledTools = new HashSet<>();
        for (String enabledTool : enabledTools) {
            normalizedEnabledTools.add(normalizeToolName(enabledTool));
        }
        for (String name : names) {
            if (normalizedEnabledTools.contains(normalizeToolName(name))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return "";
        }
        return toolName.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    private boolean containsAny(String value, String... candidates) {
        if (value == null || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 Agent 配置中的启用工具 JSON 数组。
     */
    private List<String> parseEnabledTools(String value) {
        return parseJsonArrayStrings(value, "enabledTools");
    }

    /**
     * 通用 JSON 字符串数组解析：把配置字段中的数组项转成去空格后的字符串列表。
     */
    private List<String> parseJsonArrayStrings(String value, String fieldName) {
        if (isBlank(value)) {
            return List.of();
        }
        try {
            // 配置必须是 JSON 数组，非数组结构直接视为未配置。
            JsonNode node = objectMapper.readTree(value.trim());
            if (!node.isArray()) {
                return List.of();
            }

            // 逐项提取非空字符串，避免空值进入知识库或工具调用流程。
            List<String> ids = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isNull()) {
                    String id = item.asText();
                    if (!isBlank(id)) {
                        ids.add(id.trim());
                    }
                }
            }
            return ids;
        } catch (JsonProcessingException e) {
            // 配置格式错误时只记录警告，不阻断 Agent 聊天。
            log.warn("Invalid {} stored in Agent definition: {}", fieldName, value);
            return List.of();
        }
    }

    /**
     * 根据 Agent 配置生成 OpenAI 调用参数；只有配置了模型或温度时才返回选项对象。
     */
    private OpenAiChatOptions chatOptions(AgentDefinition agent) {
        boolean hasOptions = false;
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        // 自定义模型名用于覆盖默认模型配置。
        if (!isBlank(agent.getModel())) {
            builder.model(agent.getModel().trim());
            hasOptions = true;
        }
        // 自定义温度用于控制回复的随机性。
        if (agent.getTemperature() != null) {
            builder.temperature(agent.getTemperature().doubleValue());
            hasOptions = true;
        }
        return hasOptions ? builder.build() : null;
    }

    /**
     * 校验会话是否存在、是否有效以及是否属于当前用户。
     */
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

    /**
     * 加载某个会话下的全部历史消息，并按消息 ID 正序返回，保证对话上下文顺序稳定。
     */
    private List<AgentMessage> loadMessages(Integer sessionId) {
        QueryWrapper<AgentMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByAsc("id");
        return messageMapper.selectList(query);
    }

    /**
     * 创建并保存一条会话消息，可用于用户消息、助手回复和失败兜底消息。
     */
    private AgentMessage setMessage(Integer sessionId, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    /**
     * 把数据库消息实体转换成前端接口响应 DTO。
     */
    private AgentMessageResponse toMessageResponse(AgentMessage message) {
        AgentMessageResponse response = new AgentMessageResponse();
        response.setId(message.getId());
        response.setSessionId(message.getSessionId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setCreateTime(message.getCreateTime());
        return response;
    }

    /**
     * 当会话仍是默认标题时，用第一条用户消息生成最多 24 个字符的会话标题。
     */
    private void ensureSessionTitle(AgentSession session, String content) {
        if (session == null || !"新会话".equals(session.getTitle())) {
            return;
        }

        // 标题来自用户首条消息，过长时截断，最终为空则继续使用默认标题。
        String title = content == null ? "新会话" : content.trim();
        if (title.length() > 24) {
            title = title.substring(0, 24);
        }
        session.setTitle(firstNotBlank(title, "新会话"));
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 刷新会话更新时间，用于让最近对话在列表中排到更靠前的位置。
     */
    private void touchSession(Integer sessionId) {
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    /**
     * 确定会话创建人：优先使用登录上下文中的用户 ID，没有上下文时兼容请求体传入的 userId。
     */
    private Integer currentUserId(AgentSessionCreateRequest request) {
        Integer currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null) {
            return currentUserId;
        }
        return request == null ? null : request.getUserId();
    }

    /**
     * 返回第一个非空白字符串；主值为空时使用兜底值。
     */
    private String firstNotBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    /**
     * 判断字符串是否为 null 或只包含空白字符。
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
