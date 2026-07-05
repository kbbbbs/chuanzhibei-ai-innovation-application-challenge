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
     * 知识库搜索结果中可能承载正文内容的字段名，用于从不同返回结构里抽取可拼入 Prompt 的文本片段。
     */
    private static final Set<String> KNOWLEDGE_TEXT_FIELDS = Set.of(
            "content", "text", "answer", "chunk", "snippet", "summary", "title",
            "pagecontent", "documentcontent", "data", "result", "results"
    );

    /**
     * 外部依赖：模型调用、配置、数据库 Mapper、JSON 处理、知识库检索、工具调用和流式执行线程池。
     */
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

    /**
     * Spring 注入使用的构造方法，默认创建一个缓存线程池承载 SSE 流式响应任务。
     */
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

    /**
     * 测试或内部构造使用的简化构造方法，允许调用方注入自定义执行器并跳过知识库、工具服务依赖。
     */
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

    /**
     * 统一初始化入口，负责设置默认配置、默认 ObjectMapper，并识别当前服务是否拥有线程池生命周期。
     */
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

        // 构造包含系统提示词、知识库/工具上下文和历史消息的模型 Prompt，并先落库用户消息。
        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        // 调用模型失败时记录一条兜底助手消息，便于前端和历史记录展示失败轨迹。
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
        Prompt prompt = buildPrompt(agent, loadMessages(sessionId), content);
        AgentMessage userMessage = setMessage(sessionId, "user", content);
        ensureSessionTitle(session, content);

        // 返回 SseEmitter 给 Controller，同时在线程池中持续拉取模型增量回复。
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor.execute(() -> streamModelReply(emitter, sessionId, userMessage.getId(), prompt));
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
            // 订阅模型流式输出：每个增量片段都会立即推送给前端，并累计成完整回复。
            chatModel.stream(prompt)
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
     * 根据 Agent 绑定的知识库 ID 调用 WeKnora 检索，并把检索结果格式化为可拼入 Prompt 的上下文。
     */
    private String retrieveKnowledgeContext(AgentDefinition agent, String currentUserContent) {
        List<String> knowledgeBaseIds = parseKnowledgeBaseIds(agent.getKnowledgeBaseIds());
        // 未绑定知识库、服务未注入或上下文预算关闭时，跳过知识库检索。
        if (knowledgeBaseIds.isEmpty()
                || weKnoraService == null
                || chatProperties.getKnowledgeContextMaxSnippets() <= 0
                || chatProperties.getKnowledgeContextMaxCharacters() <= 0) {
            return "";
        }

        try {
            // 使用当前用户问题作为检索 query，成功时继续抽取文本片段。
            Result result = weKnoraService.search(new WeKnoraSearchRequest(knowledgeBaseIds, currentUserContent.trim()));
            if (result == null || !"200".equals(result.getCode()) || result.getData() == null) {
                return "";
            }
            return formatKnowledgeContext(result.getData());
        } catch (Exception e) {
            // 知识库检索失败不阻断聊天，只记录日志并让模型在无知识库上下文下继续回答。
            log.warn("Agent knowledge search failed, continue without knowledge context", e);
            return "";
        }
    }

    /**
     * 解析 Agent 配置中的知识库 ID JSON 数组。
     */
    private List<String> parseKnowledgeBaseIds(String value) {
        return parseJsonArrayStrings(value, "knowledgeBaseIds");
    }

    /**
     * 根据 Agent 启用的工具列表构建工具上下文，供模型结合平台业务数据回答。
     */
    private String retrieveToolContext(AgentDefinition agent, String currentUserContent) {
        List<String> enabledTools = parseEnabledTools(agent.getEnabledTools());
        // 未启用工具、服务未注入或上下文预算关闭时，跳过工具调用。
        if (enabledTools.isEmpty()
                || agentToolService == null
                || chatProperties.getToolContextMaxItems() <= 0
                || chatProperties.getToolContextMaxCharacters() <= 0) {
            return "";
        }

        try {
            // 工具服务内部负责按工具类型执行查询，并限制条数和字符长度。
            return agentToolService.buildToolContext(enabledTools,
                    currentUserContent.trim(),
                    chatProperties.getToolContextMaxItems(),
                    chatProperties.getToolContextMaxCharacters());
        } catch (Exception e) {
            // 工具调用失败不影响聊天主流程，只在日志中记录问题。
            log.warn("Agent tool execution failed, continue without tool context", e);
            return "";
        }
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

    private String formatKnowledgeContext(Object searchData) {
        // 把任意类型的搜索结果转成 JsonNode，便于统一遍历对象、数组和普通值。
        JsonNode root = objectMapper.valueToTree(searchData);
        List<String> snippets = new ArrayList<>();
        collectKnowledgeSnippets(root, null, snippets);
        if (snippets.isEmpty()) {
            return "";
        }

        // 按配置限制拼接片段总长度，避免知识库内容过长挤占模型上下文窗口。
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

    /**
     * 递归遍历知识库搜索结果，从符合字段名规则的值节点中收集文本片段。
     */
    private void collectKnowledgeSnippets(JsonNode node, String fieldName, List<String> snippets) {
        if (node == null || node.isNull() || snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
            return;
        }
        // 数组节点逐个遍历元素，沿用父级字段名作为候选语义。
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectKnowledgeSnippets(child, fieldName, snippets);
            }
            return;
        }
        // 对象节点遍历每个属性，并把当前属性名传给子节点用于字段过滤。
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                collectKnowledgeSnippets(field.getValue(), field.getKey(), snippets);
                if (snippets.size() >= chatProperties.getKnowledgeContextMaxSnippets()) {
                    break;
                }
            }
            return;
        }
        // 普通值节点只有在字段名像正文、摘要、标题等文本字段时才会被收集。
        if (node.isValueNode() && shouldCollectKnowledgeField(fieldName)) {
            addKnowledgeSnippet(node.asText(), snippets);
        }
    }

    /**
     * 判断知识库结果字段是否适合抽取为文本上下文，兼容下划线和中划线命名。
     */
    private boolean shouldCollectKnowledgeField(String fieldName) {
        if (fieldName == null) {
            return true;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return KNOWLEDGE_TEXT_FIELDS.contains(normalized);
    }

    /**
     * 清洗并去重知识库文本片段，避免空白片段或重复内容进入 Prompt。
     */
    private void addKnowledgeSnippet(String value, List<String> snippets) {
        String snippet = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (isBlank(snippet) || snippets.contains(snippet)) {
            return;
        }
        snippets.add(snippet);
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
