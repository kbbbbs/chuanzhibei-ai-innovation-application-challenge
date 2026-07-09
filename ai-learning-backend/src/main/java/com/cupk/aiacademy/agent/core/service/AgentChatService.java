package com.cupk.aiacademy.agent.core.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.core.exception.AgentModelCallException;
import com.cupk.aiacademy.agent.core.llm.AgentModelDiagnostics;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.core.prompt.AgentPromptAssembler;
import com.cupk.aiacademy.agent.knowledge.KnowledgeContextProvider;
import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentActionSelectionService;
import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;
import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.agent.orchestrator.AgentActionIntentRouter;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
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
    private static final String DEFAULT_AGENT_CODE = "learning_assistant";
    private static final String DEFAULT_AGENT_ENABLED_TOOLS = "["
            + "\"course_create_plan\","
            + "\"course_update_plan\","
            + "\"course_status_plan\","
            + "\"chapter_create_plan\","
            + "\"chapter_update_plan\","
            + "\"chapter_sort_plan\","
            + "\"chapter_delete_plan\","
            + "\"lesson_create_plan\","
            + "\"lesson_update_plan\","
            + "\"lesson_sort_plan\","
            + "\"lesson_status_plan\","
            + "\"course_resource_attach_plan\","
            + "\"course_resource_remove_plan\","
            + "\"teaching_class_create_plan\","
            + "\"teaching_class_update_plan\","
            + "\"teaching_class_status_plan\","
            + "\"class_student_add_plan\","
            + "\"class_student_remove_plan\","
            + "\"class_student_status_plan\","
            + "\"notice_create_plan\","
            + "\"notice_update_plan\","
            + "\"notice_status_plan\","
            + "\"homework_create_plan\","
            + "\"homework_update_plan\","
            + "\"homework_delete_plan\","
            + "\"homework_submission_grade_plan\","
            + "\"exam_session_create_plan\","
            + "\"exam_session_close_plan\","
            + "\"exam_answer_grade_plan\","
            + "\"exam_paper_create_plan\","
            + "\"exam_paper_update_plan\","
            + "\"exam_paper_delete_plan\","
            + "\"exam_paper_question_add_plan\","
            + "\"exam_paper_question_remove_plan\","
            + "\"exam_paper_question_update_plan\","
            + "\"question_create_plan\","
            + "\"question_update_plan\","
            + "\"question_delete_plan\","
            + "\"grade_record_create_plan\","
            + "\"grade_record_update_plan\","
            + "\"grade_record_delete_plan\""
            + "]";
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
    private final AgentPromptAssembler promptAssembler;
    private final AgentActionIntentRouter actionIntentRouter;
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
                            AgentToolExecutor agentToolExecutor,
                            com.cupk.aiacademy.agent.query.NaturalLanguageQueryService naturalLanguageQueryService,
                            ObjectProvider<AgentActionSelectionService> actionSelectionServiceProvider,
                            ObjectProvider<AgentActionCatalog> actionCatalogProvider,
                            ObjectProvider<AgentActionPlanFactory> actionPlanFactoryProvider
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
                agentToolExecutor,
                naturalLanguageQueryService,
                actionSelectionServiceProvider == null ? null : actionSelectionServiceProvider.getIfAvailable(),
                actionCatalogProvider == null ? null : actionCatalogProvider.getIfAvailable(),
                actionPlanFactoryProvider == null ? null : actionPlanFactoryProvider.getIfAvailable()
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
                      AgentToolExecutor agentToolExecutor,
                      com.cupk.aiacademy.agent.query.NaturalLanguageQueryService naturalLanguageQueryService
     ) {
        this(chatModelClient, chatProperties, agentDefinitionMapper, sessionMapper, messageMapper, streamExecutor,
                objectMapper, knowledgeContextProvider, agentToolExecutor, naturalLanguageQueryService,
                null, null, null);
    }

    AgentChatService(ChatModelClient chatModelClient,
                     AgentChatProperties chatProperties,
                     AgentDefinitionMapper agentDefinitionMapper,
                     AgentSessionMapper sessionMapper,
                     AgentMessageMapper messageMapper,
                     Executor streamExecutor,
                     ObjectMapper objectMapper,
                     KnowledgeContextProvider knowledgeContextProvider,
                     AgentToolExecutor agentToolExecutor,
                     com.cupk.aiacademy.agent.query.NaturalLanguageQueryService naturalLanguageQueryService,
                     AgentActionSelectionService actionSelectionService,
                     AgentActionCatalog actionCatalog,
                     AgentActionPlanFactory actionPlanFactory
     ) {
        this.chatModelClient = chatModelClient;
        this.chatProperties = chatProperties == null ? new AgentChatProperties() : chatProperties;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.knowledgeContextProvider = knowledgeContextProvider;
        this.agentToolExecutor = agentToolExecutor;
        AgentConfigurationParser configurationParser = new AgentConfigurationParser(this.objectMapper);
        this.promptAssembler = new AgentPromptAssembler(this.chatProperties,
                configurationParser,
                this.knowledgeContextProvider,
                this.agentToolExecutor,
                naturalLanguageQueryService);
        this.actionIntentRouter = new AgentActionIntentRouter(configurationParser, this.agentToolExecutor,
                actionSelectionService, actionCatalog, actionPlanFactory);
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

        Optional<AgentDirectActionReply> directReply = actionIntentRouter.buildDirectActionReply(agent, content);
        if (directReply.isPresent()) {
            AgentDirectActionReply reply = directReply.get();
            AgentMessage userMessage = setMessage(sessionId, "user", content);
            ensureSessionTitle(session, content);
            AgentMessage assistantMessage = setMessage(sessionId, "assistant", reply.content());
            touchSession(sessionId);
            return toChatResponse(sessionId, userMessage.getId(), assistantMessage, reply.pendingAction());
        }

        applyProviderModelCompatibility(agent);

        // 构造包含系统提示词、知识库/工具上下文和历史消息的模型 Prompt，并先落库用户消息。
        Prompt prompt = promptAssembler.buildPrompt(agent, loadMessages(sessionId), content);
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
            throw recordModelFailure(sessionId,
                    userMessage.getId(),
                    emptyModelResponseFailure(sessionId, userMessage.getId(), null));
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
        Optional<AgentDirectActionReply> directReply = actionIntentRouter.buildDirectActionReply(agent, content);
        if (directReply.isPresent()) {
            AgentDirectActionReply reply = directReply.get();
            AgentMessage userMessage = setMessage(sessionId, "user", content);
            ensureSessionTitle(session, content);
            return streamDirectReply(sessionId, userMessage.getId(), reply);
        }

        applyProviderModelCompatibility(agent);

        Prompt prompt = promptAssembler.buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        // 返回 SseEmitter 给 Controller，同时在线程池中持续拉取模型增量回复。
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor.execute(() -> streamModelReply(emitter, sessionId, userMessage.getId(), prompt));
        return emitter;
    }

    private SseEmitter streamDirectReply(Integer sessionId, Integer userMessageId, AgentDirectActionReply reply) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor.execute(() -> {
            try {
                String assistantContent = reply.content();
                sendStreamChunk(emitter, new StringBuilder(), assistantContent);
                AgentMessage assistantMessage = setMessage(sessionId, "assistant", assistantContent);
                touchSession(sessionId);
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(toChatResponse(sessionId, userMessageId, assistantMessage, reply.pendingAction()),
                                MediaType.APPLICATION_JSON));
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
            throw modelFailure(null, null, null, e);
        }
        String assistantContent = assistantContent(chatResponse);
        if (isBlank(assistantContent)) {
            throw emptyModelResponseFailure(null, null, null);
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
                throw emptyModelResponseFailure(sessionId, userMessageId, null);
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
        AgentModelCallException exception;
        if (cause instanceof AgentModelCallException agentException) {
            AgentModelDiagnostics diagnostics = agentException.getDiagnostics() == null
                    ? AgentModelDiagnostics.from(chatModelClient.provider(), chatModelClient.configuredModel(), agentException)
                    : agentException.getDiagnostics();
            exception = modelFailure(sessionId,
                    userMessageId,
                    assistantMessage.getId(),
                    diagnostics,
                    agentException.getCause());
        } else {
            exception = modelFailure(sessionId, userMessageId, assistantMessage.getId(), cause);
        }
        log.warn("Agent model call failed: provider={}, model={}, category={}, sessionId={}, userMessageId={}, root={}",
                exception.getDiagnostics() == null ? "unknown" : exception.getDiagnostics().provider(),
                exception.getDiagnostics() == null ? "unknown" : exception.getDiagnostics().model(),
                exception.getDiagnostics() == null ? "unknown" : exception.getDiagnostics().category(),
                sessionId,
                userMessageId,
                exception.getDiagnostics() == null ? "" : exception.getDiagnostics().rootMessage(),
                cause);
        return exception;
    }

    /**
     * 把一次聊天结果转换为接口响应对象，包含会话 ID、用户消息 ID、助手消息 ID、内容和创建时间。
     */
    private AgentChatResponse toChatResponse(Integer sessionId, Integer userMessageId, AgentMessage assistantMessage) {
        return toChatResponse(sessionId, userMessageId, assistantMessage, null);
    }

    private AgentChatResponse toChatResponse(Integer sessionId,
                                             Integer userMessageId,
                                             AgentMessage assistantMessage,
                                             AgentPendingActionResponse pendingAction) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId);
        response.setUserMessageId(userMessageId);
        response.setAssistantMessageId(assistantMessage.getId());
        response.setContent(assistantMessage.getContent());
        response.setPendingAction(pendingAction);
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
        if (e.getDiagnostics() != null) {
            data.put("diagnostics", e.getDiagnostics().toData());
        }
        return data;
    }

    private AgentModelCallException modelFailure(Integer sessionId,
                                                 Integer userMessageId,
                                                 Integer assistantMessageId,
                                                 Exception cause) {
        AgentModelDiagnostics diagnostics = AgentModelDiagnostics.from(
                chatModelClient.provider(),
                chatModelClient.configuredModel(),
                cause);
        return modelFailure(sessionId, userMessageId, assistantMessageId, diagnostics, cause);
    }

    private AgentModelCallException modelFailure(Integer sessionId,
                                                 Integer userMessageId,
                                                 Integer assistantMessageId,
                                                 AgentModelDiagnostics diagnostics,
                                                 Throwable cause) {
        return new AgentModelCallException(MODEL_FAILURE_CONTENT,
                sessionId,
                userMessageId,
                assistantMessageId,
                diagnostics,
                cause);
    }

    private AgentModelCallException emptyModelResponseFailure(Integer sessionId,
                                                              Integer userMessageId,
                                                              Integer assistantMessageId) {
        return modelFailure(sessionId,
                userMessageId,
                assistantMessageId,
                AgentModelDiagnostics.emptyResponse(chatModelClient.provider(), chatModelClient.configuredModel()),
                null);
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
        applyDefaultAgentTools(agent);
        return agent;
    }

    private void applyDefaultAgentTools(AgentDefinition agent) {
        if (agent == null || !isDefaultAgent(agent) || !isBlank(agent.getEnabledTools())) {
            return;
        }
        agent.setEnabledTools(DEFAULT_AGENT_ENABLED_TOOLS);
    }

    private boolean isDefaultAgent(AgentDefinition agent) {
        return Integer.valueOf(DEFAULT_AGENT_ID).equals(agent.getId())
                || DEFAULT_AGENT_CODE.equals(agent.getCode());
    }

    private void applyProviderModelCompatibility(AgentDefinition agent) {
        if (agent == null
                || !isDeepSeekProvider()
                || isBlank(agent.getModel())
                || isDeepSeekModel(agent.getModel())) {
            return;
        }
        log.warn("Agent model {} is incompatible with provider deepseek, fallback to configured model {}",
                agent.getModel(),
                chatModelClient.configuredModel());
        agent.setModel(null);
    }

    private boolean isDeepSeekProvider() {
        return "deepseek".equalsIgnoreCase(chatModelClient.provider());
    }

    private boolean isDeepSeekModel(String model) {
        return model != null && model.trim().toLowerCase(Locale.ROOT).startsWith("deepseek-");
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
