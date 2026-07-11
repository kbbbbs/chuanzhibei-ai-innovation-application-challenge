package com.cupk.aiacademy.agent.core.service;

import com.cupk.aiacademy.agent.core.config.AgentChatProperties;
import com.cupk.aiacademy.agent.action.AgentActionClarificationDraft;
import com.cupk.aiacademy.agent.action.AgentPendingActionResponse;
import com.cupk.aiacademy.agent.action.AgentPendingActionStore;
import com.cupk.aiacademy.agent.core.exception.AgentModelCallException;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.knowledge.KnowledgeContextProvider;
import com.cupk.aiacademy.agent.orchestrator.AgentDirectActionReply;
import com.cupk.aiacademy.agent.planner.SchemaDrivenAgentPlanner;
import com.cupk.aiacademy.agent.planner.SchemaDrivenReadToolPlanner;
import com.cupk.aiacademy.agent.planner.AgentPlanningException;
import com.cupk.aiacademy.agent.planner.AgentPlanningOutcome;
import com.cupk.aiacademy.agent.planner.UnifiedAgentPlanner;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.AgentChatResponse;
import com.cupk.aiacademy.dto.AgentSessionUpdateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.mapper.AgentMessageMapper;
import com.cupk.aiacademy.mapper.AgentSessionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.pojo.AgentMessage;
import com.cupk.aiacademy.pojo.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent 聊天服务测试，重点覆盖会话归属校验，防止用户读取他人的会话消息。
 */
@ExtendWith(MockitoExtension.class)
class AgentChatServiceTest {

    @Mock
    private ChatModelClient chatModelClient;

    @Mock
    private AgentDefinitionMapper agentDefinitionMapper;

    @Mock
    private AgentSessionMapper sessionMapper;

    @Mock
    private AgentMessageMapper messageMapper;

    @Mock
    private KnowledgeContextProvider knowledgeContextProvider;

    @Mock
    private AgentToolExecutor agentToolExecutor;

    @Mock
    private SchemaDrivenAgentPlanner schemaDrivenAgentPlanner;

    @Mock
    private SchemaDrivenReadToolPlanner schemaDrivenReadToolPlanner;

    @Mock
    private UnifiedAgentPlanner unifiedAgentPlanner;

    @Mock
    private AgentPendingActionStore pendingActionStore;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void listMessagesRejectsOtherUsersSession() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 20));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.listMessages(1));

        assertEquals("无权限访问该 Agent 会话", exception.getMessage());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void listMessagesAllowsOwnerSession() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1, "user", "你好")));

        var messages = service.listMessages(1);

        assertEquals(1, messages.size());
        assertEquals("你好", messages.get(0).getContent());
        verify(messageMapper).selectList(any());
    }

    @Test
    void listMessagesRestoresStructuredPendingAction() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10));
        AgentMessage assistant = message(2, "assistant", "请确认发布作业");
        assistant.setToolName("pending_action");
        assistant.setToolResult("""
                {"actionId":"action-1","actionCode":"homework.create","expired":false}
                """);
        when(messageMapper.selectList(any())).thenReturn(List.of(assistant));

        List<com.cupk.aiacademy.dto.AgentMessageResponse> messages = service.listMessages(1);

        assertEquals("action-1", messages.getFirst().getPendingAction().actionId());
        assertEquals("homework.create", messages.getFirst().getPendingAction().actionCode());
    }

    @Test
    void listMessagesMarksRemovedPendingActionAsFinished() {
        AgentChatService service = newService(pendingActionStore);
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10));
        AgentMessage assistant = message(2, "assistant", "动作待确认");
        assistant.setToolName("pending_action");
        assistant.setToolResult("""
                {"actionId":"action-1","actionCode":"homework.create","expired":false}
                """);
        when(messageMapper.selectList(any())).thenReturn(List.of(assistant));
        when(pendingActionStore.findById("action-1")).thenReturn(Optional.empty());

        List<com.cupk.aiacademy.dto.AgentMessageResponse> messages = service.listMessages(1);

        assertNull(messages.getFirst().getPendingAction());
        assertEquals("动作已结束", messages.getFirst().getContent());
    }

    @Test
    void updateSessionRenamesOwnerSession() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10));
        AgentSessionUpdateRequest request = new AgentSessionUpdateRequest();
        request.setTitle("  新标题  ");

        AgentSession updated = service.updateSession(1, request);

        assertEquals("新标题", updated.getTitle());
        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals("新标题", captor.getValue().getTitle());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    void deleteSessionMarksOwnerSessionDeleted() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10));

        service.deleteSession(1);

        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void createSessionRejectsInactiveAgent() {
        AgentChatService service = newService();
        when(agentDefinitionMapper.selectById(1)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createSession(null));

        assertEquals("Agent 不存在或已停用", exception.getMessage());
        verifyNoInteractions(sessionMapper);
    }

    @Test
    void createSessionResolvesDefaultAgentByCodeWhenDefaultIdIsNotOne() {
        AgentChatService service = newService();
        AgentDefinition defaultAgent = agentDefinition(102);
        defaultAgent.setCode("learning_assistant");
        when(agentDefinitionMapper.selectById(1)).thenReturn(null);
        when(agentDefinitionMapper.selectOne(any())).thenReturn(defaultAgent);

        AgentSession session = service.createSession(null);

        assertEquals(102, session.getAgentId());
        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(102, captor.getValue().getAgentId());
    }

    @Test
    void chatUsesAgentDefinitionPromptAndOptions() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1, "user", "讲一下递归")));
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("递归是一种函数调用自身的方式。"))
        )));

        service.chat(1, chatRequest("讲一下递归"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(captor.capture(), any(java.time.Duration.class));
        Prompt prompt = captor.getValue();
        assertTrue(prompt.getInstructions().get(0).getContent().startsWith("你是 Java 课程助教。"));
        assertTrue(prompt.getInstructions().get(0).getContent().contains("默认使用简洁、自然的中文"));
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
        assertEquals("deepseek-test", options.getModel());
        assertEquals(0.3, options.getTemperature());
    }

    @Test
    void chatPromptSanitizesPendingActionHistory() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        AgentMessage pending = message(2, "assistant", "冗长的待确认说明和确认ID=secret-action-id");
        pending.setToolName("pending_action");
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1, "user", "创建一门课程"),
                pending
        ));
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class)))
                .thenReturn(chatResponse("好的"));

        service.chat(1, chatRequest("现在几点"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(captor.capture(), any(java.time.Duration.class));
        List<?> instructions = captor.getValue().getInstructions();
        assertTrue(instructions.stream().anyMatch(message ->
                "系统已接管该操作请求。".equals(((org.springframework.ai.chat.messages.Message) message).getContent())));
        assertTrue(instructions.stream().noneMatch(message ->
                ((org.springframework.ai.chat.messages.Message) message).getContent().contains("secret-action-id")));
    }

    @Test
    void chatIgnoresIncompatibleAgentModelWhenProviderIsDeepSeek() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setModel("claude-3");
        when(chatModelClient.provider()).thenReturn("deepseek");
        when(chatModelClient.configuredModel()).thenReturn("deepseek-v4-flash");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("讲一下递归"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(captor.capture(), any(java.time.Duration.class));
        OpenAiChatOptions options = (OpenAiChatOptions) captor.getValue().getOptions();
        assertNull(options.getModel());
        assertEquals(0.3, options.getTemperature());
    }

    @Test
    void chatAddsKnowledgeSearchResultToPromptWhenAgentHasKnowledgeBases() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setKnowledgeBaseIds("[\"kb-java\",2]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(knowledgeContextProvider.retrieve(agent, "什么是递归"))
                .thenReturn("[1] 递归是函数调用自身的方式。\n[2] 递归必须设置终止条件。");
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("什么是递归"));

        verify(knowledgeContextProvider).retrieve(agent, "什么是递归");
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(captor.capture(), any(java.time.Duration.class));
        String systemPrompt = captor.getValue().getInstructions().get(0).getContent();
        assertTrue(systemPrompt.contains("知识库参考资料"));
        assertTrue(systemPrompt.contains("默认使用简洁、自然的中文"));
        assertTrue(systemPrompt.contains("递归是函数调用自身的方式。"));
        assertTrue(systemPrompt.contains("递归必须设置终止条件。"));
        assertEquals("什么是递归", captor.getValue().getInstructions()
                .get(captor.getValue().getInstructions().size() - 1).getContent());
    }

    @Test
    void chatAddsModelSelectedReadToolResultToPrompt() {
        AgentChatService service = newService(new AgentChatProperties(), null, schemaDrivenReadToolPlanner);
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_search\",\"learning_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(schemaDrivenReadToolPlanner.planToolContext(agent, "推荐一下 Java 学习计划"))
                .thenReturn("[learning_plan]\n建议每天学习 30 分钟");
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("推荐一下 Java 学习计划"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(captor.capture(), any(java.time.Duration.class));
        String systemPrompt = captor.getValue().getInstructions().get(0).getContent();
        assertTrue(systemPrompt.contains("工具调用结果"));
        assertTrue(systemPrompt.contains("建议每天学习 30 分钟"));
        verify(schemaDrivenReadToolPlanner).planToolContext(agent, "推荐一下 Java 学习计划");
    }

    @Test
    void chatUsesSchemaPlannerForWriteRequest() {
        AgentChatService service = newService(new AgentChatProperties(), schemaDrivenAgentPlanner, null);
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"homework_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(schemaDrivenAgentPlanner.tryPlan(agent, "给 Java 入门布置第一次练习"))
                .thenReturn(java.util.Optional.of(new AgentDirectActionReply("请确认操作计划", null)));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            message.setCreateTime(LocalDateTime.now());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        AgentChatResponse response = service.chat(1, chatRequest("给 Java 入门布置第一次练习"));

        assertEquals("请确认操作计划", response.getContent());
        verify(schemaDrivenAgentPlanner).tryPlan(agent, "给 Java 入门布置第一次练习");
        verifyNoInteractions(chatModelClient);
    }

    @org.junit.jupiter.api.Nested
    @org.junit.jupiter.api.Disabled("关键词规则路由已淘汰；由 Schema Planner 测试替代")
    class LegacyRuleRouterTests {

    @Test
    void chatRoutesNoticeCreateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"notice_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("notice_create_plan")),
                org.mockito.ArgumentMatchers.eq("帮我发布一条通知，提醒一班明天交作业"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "notice_create_plan",
                "操作计划=发布通知；需要确认=是；确认ID=action-1",
                Map.of("actionCode", "notice.create", "actionId", "action-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("帮我发布一条通知，提醒一班明天交作业"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        assertEquals("action-1", response.getPendingAction().actionId());
        assertEquals("notice.create", response.getPendingAction().actionCode());
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseCreateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_create_plan")),
                org.mockito.ArgumentMatchers.eq("新建一门课程，名字叫我只睡了5个多小时，课程分类1"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_create_plan",
                "操作计划=创建课程；需要确认=是；确认ID=action-course-1",
                Map.of("actionCode", "course.create", "actionId", "action-course-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("新建一门课程，名字叫我只睡了5个多小时，课程分类1"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatUsesDefaultAgentWritePlanToolsWhenDefaultAgentHasNoToolConfig() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(1);
        agent.setCode("learning_assistant");
        agent.setEnabledTools(null);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 1));
        when(agentDefinitionMapper.selectById(1)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_create_plan")),
                org.mockito.ArgumentMatchers.eq("帮我创建一门课程，标题是我不想吃肉，分类ID是1"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_create_plan",
                "操作计划=创建课程；需要确认=是；确认ID=action-course-default",
                Map.of("actionCode", "course.create", "actionId", "action-course-default")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("帮我创建一门课程，标题是我不想吃肉，分类ID是1"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        assertEquals("action-course-default", response.getPendingAction().actionId());
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesLessonCreateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"lesson_create_plan\",\"chapter_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("lesson_create_plan")),
                org.mockito.ArgumentMatchers.eq("给第一章新增一个课时，标题是变量与数据类型"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "lesson_create_plan",
                "操作计划=创建课时；需要确认=是；确认ID=action-lesson-1",
                Map.of("actionCode", "lesson.create", "actionId", "action-lesson-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("给第一章新增一个课时，标题是变量与数据类型"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesLessonUpdateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"lesson_update_plan\",\"chapter_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("lesson_update_plan")),
                org.mockito.ArgumentMatchers.eq("把 41 号课时标题改为变量、常量与数据类型"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "lesson_update_plan",
                "操作计划=修改课时；需要确认=是；确认ID=action-lesson-2",
                Map.of("actionCode", "lesson.update", "actionId", "action-lesson-2")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 41 号课时标题改为变量、常量与数据类型"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseResourceAttachRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_resource_attach_plan\",\"lesson_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_resource_attach_plan")),
                org.mockito.ArgumentMatchers.eq("给 41 号课时挂载视频资源"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_resource_attach_plan",
                "操作计划=挂载课程资源；需要确认=是；确认ID=action-resource-1",
                Map.of("actionCode", "course.resource.attach", "actionId", "action-resource-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("给 41 号课时挂载视频资源"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseResourceRemoveRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_resource_remove_plan\",\"lesson_status_plan\",\"lesson_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_resource_remove_plan")),
                org.mockito.ArgumentMatchers.eq("移除 41 号课时的视频资源"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_resource_remove_plan",
                "操作计划=移除课程资源；风险等级=HIGH；二次确认文本=确认移除课程资源；确认ID=action-resource-2",
                Map.of("actionCode", "course.resource.remove", "actionId", "action-resource-2",
                        "confirmationText", "确认移除课程资源")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("移除 41 号课时的视频资源"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesLessonSortRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"lesson_sort_plan\",\"lesson_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("lesson_sort_plan")),
                org.mockito.ArgumentMatchers.eq("调整第一章课时顺序，把运算符排到变量前面"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "lesson_sort_plan",
                "操作计划=调整课时排序；需要确认=是；确认ID=action-lesson-3",
                Map.of("actionCode", "lesson.sort", "actionId", "action-lesson-3")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("调整第一章课时顺序，把运算符排到变量前面"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesLessonStatusRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"lesson_status_plan\",\"lesson_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("lesson_status_plan")),
                org.mockito.ArgumentMatchers.eq("下架 41 号课时"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "lesson_status_plan",
                "操作计划=变更课时状态；风险等级=HIGH；二次确认文本=确认变更课时状态；确认ID=action-lesson-4",
                Map.of("actionCode", "lesson.status", "actionId", "action-lesson-4",
                        "confirmationText", "确认变更课时状态")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("下架 41 号课时"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesChapterCreateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"chapter_create_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("chapter_create_plan")),
                org.mockito.ArgumentMatchers.eq("给 12 号课程新增第一章 Java 入门"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "chapter_create_plan",
                "操作计划=创建章节；需要确认=是；确认ID=action-chapter-1",
                Map.of("actionCode", "chapter.create", "actionId", "action-chapter-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("给 12 号课程新增第一章 Java 入门"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesChapterUpdateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"chapter_update_plan\",\"course_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("chapter_update_plan")),
                org.mockito.ArgumentMatchers.eq("把 21 号章节标题改为 Java 基础"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "chapter_update_plan",
                "操作计划=修改章节；需要确认=是；确认ID=action-chapter-2",
                Map.of("actionCode", "chapter.update", "actionId", "action-chapter-2")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 21 号章节标题改为 Java 基础"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesChapterSortRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"chapter_sort_plan\",\"chapter_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("chapter_sort_plan")),
                org.mockito.ArgumentMatchers.eq("调整 12 号课程章节顺序，把第二章排到第一章前面"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "chapter_sort_plan",
                "操作计划=调整章节排序；需要确认=是；确认ID=action-chapter-3",
                Map.of("actionCode", "chapter.sort", "actionId", "action-chapter-3")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("调整 12 号课程章节顺序，把第二章排到第一章前面"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesChapterDeleteRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"chapter_delete_plan\",\"chapter_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("chapter_delete_plan")),
                org.mockito.ArgumentMatchers.eq("删除 21 号章节"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "chapter_delete_plan",
                "操作计划=删除章节；风险等级=HIGH；二次确认文本=确认删除章节；确认ID=action-chapter-4",
                Map.of("actionCode", "chapter.delete", "actionId", "action-chapter-4",
                        "confirmationText", "确认删除章节")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("删除 21 号章节"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesClassStudentAddRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"class_student_add_plan\",\"teaching_class_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("class_student_add_plan")),
                org.mockito.ArgumentMatchers.eq("把 30 号学生加入 51 号教学班"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "class_student_add_plan",
                "操作计划=添加班级学生；需要确认=是；确认ID=action-class-student-1",
                Map.of("actionCode", "class.student.add", "actionId", "action-class-student-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 30 号学生加入 51 号教学班"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesClassStudentRemoveRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"class_student_remove_plan\",\"class_student_status_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("class_student_remove_plan")),
                org.mockito.ArgumentMatchers.eq("从 51 号教学班移除 30 号学生"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "class_student_remove_plan",
                "操作计划=移除班级学生；风险等级=HIGH；二次确认文本=确认移除班级学生；确认ID=action-class-student-2",
                Map.of("actionCode", "class.student.remove", "actionId", "action-class-student-2",
                        "confirmationText", "确认移除班级学生")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("从 51 号教学班移除 30 号学生"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesClassStudentStatusRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"class_student_status_plan\",\"teaching_class_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("class_student_status_plan")),
                org.mockito.ArgumentMatchers.eq("把 51 号教学班的 30 号学生状态改为休学"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "class_student_status_plan",
                "操作计划=变更班级学生状态；风险等级=HIGH；二次确认文本=确认变更班级学生状态；确认ID=action-class-student-3",
                Map.of("actionCode", "class.student.status", "actionId", "action-class-student-3",
                        "confirmationText", "确认变更班级学生状态")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 51 号教学班的 30 号学生状态改为休学"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesTeachingClassCreateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"teaching_class_create_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("teaching_class_create_plan")),
                org.mockito.ArgumentMatchers.eq("给 12 号课程新建 Java 基础一班教学班"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "teaching_class_create_plan",
                "操作计划=创建教学班；需要确认=是；确认ID=action-class-1",
                Map.of("actionCode", "teaching.class.create", "actionId", "action-class-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("给 12 号课程新建 Java 基础一班教学班"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesTeachingClassUpdateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"teaching_class_update_plan\",\"course_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("teaching_class_update_plan")),
                org.mockito.ArgumentMatchers.eq("把 51 号教学班名称改为 Java 进阶一班"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "teaching_class_update_plan",
                "操作计划=修改教学班；需要确认=是；确认ID=action-class-2",
                Map.of("actionCode", "teaching.class.update", "actionId", "action-class-2")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 51 号教学班名称改为 Java 进阶一班"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesTeachingClassStatusRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"teaching_class_status_plan\",\"teaching_class_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("teaching_class_status_plan")),
                org.mockito.ArgumentMatchers.eq("结束 51 号教学班"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "teaching_class_status_plan",
                "操作计划=变更教学班状态；风险等级=HIGH；二次确认文本=确认变更教学班状态；确认ID=action-class-3",
                Map.of("actionCode", "teaching.class.status", "actionId", "action-class-3",
                        "confirmationText", "确认变更教学班状态")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("结束 51 号教学班"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseUpdateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_update_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_update_plan")),
                org.mockito.ArgumentMatchers.eq("把 12 号课程名称改为 Java 进阶"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_update_plan",
                "操作计划=修改课程；需要确认=是；确认ID=action-course-2",
                Map.of("actionCode", "course.update", "actionId", "action-course-2")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 12 号课程名称改为 Java 进阶"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseTitleOnlyRenameRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_update_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_update_plan")),
                org.mockito.ArgumentMatchers.eq("修改标题是我不想吃肉，改成我想吃肉"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_update_plan",
                "操作计划=修改课程；需要确认=是；确认ID=action-course-title-rename",
                Map.of("actionCode", "course.update", "actionId", "action-course-title-rename")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("修改标题是我不想吃肉，改成我想吃肉"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseStatusRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_status_plan\",\"course_update_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_status_plan")),
                org.mockito.ArgumentMatchers.eq("下架 12 号课程"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_status_plan",
                "操作计划=变更课程状态；风险等级=HIGH；二次确认文本=确认变更课程状态；确认ID=action-course-3",
                Map.of("actionCode", "course.status", "actionId", "action-course-3",
                        "confirmationText", "确认变更课程状态")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("下架 12 号课程"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseStatusByCourseNameRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_status_plan\",\"course_update_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_status_plan")),
                org.mockito.ArgumentMatchers.eq("下架课程名为高勋的课"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_status_plan",
                "操作计划=变更课程状态；风险等级=HIGH；二次确认文本=确认变更课程状态；确认ID=action-course-status-title-1",
                Map.of("actionCode", "course.status", "actionId", "action-course-status-title-1",
                        "confirmationText", "确认变更课程状态")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("下架课程名为高勋的课"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseDeleteByTitleRequestToDeletePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_delete_plan\",\"course_status_plan\",\"course_update_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_delete_plan")),
                org.mockito.ArgumentMatchers.eq("删除标题是我有点想吃肉的课，授课老师王老师，难度入门，分类ID是1"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_delete_plan",
                "操作计划=删除课程；风险等级=HIGH；二次确认文本=确认删除课程；确认ID=action-course-delete-1",
                Map.of("actionCode", "course.delete", "actionId", "action-course-delete-1",
                        "confirmationText", "确认删除课程")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("删除标题是我有点想吃肉的课，授课老师王老师，难度入门，分类ID是1"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseStatusDisableByCourseNameRequestToStatusPlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_status_plan\",\"course_update_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_status_plan")),
                org.mockito.ArgumentMatchers.eq("停用课名叫 Java 入门的课程"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_status_plan",
                "操作计划=变更课程状态；风险等级=HIGH；二次确认文本=确认变更课程状态；确认ID=action-course-disable-1",
                Map.of("actionCode", "course.status", "actionId", "action-course-disable-1",
                        "confirmationText", "确认变更课程状态")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("停用课名叫 Java 入门的课程"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesCourseRestoreByCourseNameRequestToStatusPlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_status_plan\",\"course_update_plan\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("course_status_plan")),
                org.mockito.ArgumentMatchers.eq("恢复 Java 入门 课程"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "course_status_plan",
                "操作计划=变更课程状态；风险等级=HIGH；二次确认文本=确认变更课程状态；确认ID=action-course-restore-1",
                Map.of("actionCode", "course.status", "actionId", "action-course-restore-1",
                        "confirmationText", "确认变更课程状态")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("恢复 Java 入门 课程"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesHomeworkCreateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"homework_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("homework_create_plan")),
                org.mockito.ArgumentMatchers.eq("帮我布置第一章作业，明天截止"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "homework_create_plan",
                "操作计划=发布作业；需要确认=是；确认ID=action-2",
                Map.of("actionCode", "homework.create", "actionId", "action-2")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("帮我布置第一章作业，明天截止"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesHomeworkUpdateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"homework_update_plan\",\"homework_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("homework_update_plan")),
                org.mockito.ArgumentMatchers.eq("把 88 号作业截止时间改到明天 18 点"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "homework_update_plan",
                "操作计划=修改作业；需要确认=是；确认ID=action-8",
                Map.of("actionCode", "homework.update", "actionId", "action-8")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 88 号作业截止时间改到明天 18 点"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesHomeworkDeleteRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"homework_delete_plan\",\"homework_update_plan\",\"homework_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("homework_delete_plan")),
                org.mockito.ArgumentMatchers.eq("删除 88 号作业"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "homework_delete_plan",
                "操作计划=删除作业；风险等级=HIGH；二次确认文本=确认删除作业；确认ID=action-9",
                Map.of("actionCode", "homework.delete", "actionId", "action-9", "confirmationText", "确认删除作业")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("删除 88 号作业"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesHomeworkSubmissionGradeRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"homework_submission_grade_plan\",\"homework_delete_plan\",\"homework_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("homework_submission_grade_plan")),
                org.mockito.ArgumentMatchers.eq("批改 501 号作业提交，给 92 分，反馈完成质量较高"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "homework_submission_grade_plan",
                "操作计划=批改作业提交；风险等级=HIGH；二次确认文本=确认批改作业；确认ID=action-10",
                Map.of("actionCode", "homework.submission.grade", "actionId", "action-10",
                        "confirmationText", "确认批改作业")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("批改 501 号作业提交，给 92 分，反馈完成质量较高"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesExamSessionCreateRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"exam_session_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("exam_session_create_plan")),
                org.mockito.ArgumentMatchers.eq("帮我创建一场 Java 期末考试"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "exam_session_create_plan",
                "操作计划=创建考试安排；风险等级=HIGH；二次确认文本=确认创建考试；确认ID=action-3",
                Map.of("actionCode", "exam.session.create", "actionId", "action-3", "confirmationText", "确认创建考试")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("帮我创建一场 Java 期末考试"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesExamSessionCloseRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"exam_session_close_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("exam_session_close_plan")),
                org.mockito.ArgumentMatchers.eq("帮我关闭 99 号考试"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "exam_session_close_plan",
                "操作计划=关闭考试安排；风险等级=HIGH；二次确认文本=确认关闭考试；确认ID=action-4",
                Map.of("actionCode", "exam.session.close", "actionId", "action-4", "confirmationText", "确认关闭考试")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("帮我关闭 99 号考试"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesExamAnswerGradeRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"exam_answer_grade_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("exam_answer_grade_plan")),
                org.mockito.ArgumentMatchers.eq("帮我批改 2001 号答卷的 3001 号主观题，给 8 分"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "exam_answer_grade_plan",
                "操作计划=批改考试答题；风险等级=HIGH；二次确认文本=确认批改考试；确认ID=action-5",
                Map.of("actionCode", "exam.answer.grade", "actionId", "action-5", "confirmationText", "确认批改考试")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("帮我批改 2001 号答卷的 3001 号主观题，给 8 分"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesQuestionCreateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"question_create_plan\",\"exam_session_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("question_create_plan")),
                org.mockito.ArgumentMatchers.eq("给 Java 课程新增一道单选题"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "question_create_plan",
                "操作计划=创建试题；需要确认=是；确认ID=action-question-1",
                Map.of("actionCode", "question.create", "actionId", "action-question-1")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("给 Java 课程新增一道单选题"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesQuestionUpdateRequestToWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"question_update_plan\",\"question_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("question_update_plan")),
                org.mockito.ArgumentMatchers.eq("把 3001 号试题题干改为 Java int 默认值是多少"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "question_update_plan",
                "操作计划=修改试题；需要确认=是；确认ID=action-question-2",
                Map.of("actionCode", "question.update", "actionId", "action-question-2")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 3001 号试题题干改为 Java int 默认值是多少"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesQuestionDeleteRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"question_delete_plan\",\"question_update_plan\",\"question_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("question_delete_plan")),
                org.mockito.ArgumentMatchers.eq("删除 3001 号试题"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "question_delete_plan",
                "操作计划=删除试题；风险等级=HIGH；二次确认文本=确认删除试题；确认ID=action-question-3",
                Map.of("actionCode", "question.delete", "actionId", "action-question-3",
                        "confirmationText", "确认删除试题")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("删除 3001 号试题"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesGradeRecordCreateRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"grade_record_create_plan\",\"grade_record_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("grade_record_create_plan")),
                org.mockito.ArgumentMatchers.eq("给学生 30 录入 Java 期末成绩 95 分"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "grade_record_create_plan",
                "操作计划=录入成绩记录；风险等级=HIGH；二次确认文本=确认录入成绩；确认ID=action-6",
                Map.of("actionCode", "grade.record.create", "actionId", "action-6", "confirmationText", "确认录入成绩")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("给学生 30 录入 Java 期末成绩 95 分"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesGradeRecordDeleteRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"grade_record_delete_plan\",\"grade_record_create_plan\",\"grade_record_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("grade_record_delete_plan")),
                org.mockito.ArgumentMatchers.eq("删除 1001 号成绩记录"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "grade_record_delete_plan",
                "操作计划=删除成绩记录；风险等级=HIGH；二次确认文本=确认删除成绩；确认ID=action-7",
                Map.of("actionCode", "grade.record.delete", "actionId", "action-7", "confirmationText", "确认删除成绩")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("删除 1001 号成绩记录"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatRoutesGradeRecordUpdateRequestToHighRiskWritePlanWithoutCallingModel() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"grade_record_update_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(agentToolExecutor.executeTools(
                org.mockito.ArgumentMatchers.eq(List.of("grade_record_update_plan")),
                org.mockito.ArgumentMatchers.eq("把 1001 号成绩改成 95 分"),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(List.of(AgentToolResult.writePlan(
                "grade_record_update_plan",
                "操作计划=修改成绩记录；风险等级=HIGH；二次确认文本=确认修改成绩；确认ID=action-5",
                Map.of("actionCode", "grade.record.update", "actionId", "action-5", "confirmationText", "确认修改成绩")
        )));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        var response = service.chat(1, chatRequest("把 1001 号成绩改成 95 分"));

        assertEquals(101, response.getAssistantMessageId());
        assertDirectActionReply(response);
        verifyNoInteractions(chatModelClient);
    }

    }


    @Test
    void chatRecordsFailureMessageWhenModelCallFails() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1, "user", "讲一下递归")));
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class)))
                .thenThrow(new RuntimeException("timeout"));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        AgentModelCallException exception = assertThrows(AgentModelCallException.class,
                () -> service.chat(1, chatRequest("讲一下递归")));

        assertEquals("Agent 响应超时，请重新发送。", exception.getMessage());
        assertEquals(100, exception.getUserMessageId());
        assertEquals(101, exception.getAssistantMessageId());
        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("user", captor.getAllValues().get(0).getRole());
        assertEquals("assistant", captor.getAllValues().get(1).getRole());
        assertEquals("Agent 响应超时，请重新发送。", captor.getAllValues().get(1).getContent());
    }

    @Test
    void unifiedPlannerFailureBecomesSpecificAssistantReply() {
        AgentChatService service = newService(new AgentChatProperties(), unifiedAgentPlanner);
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(unifiedAgentPlanner.plan(any(AgentDefinition.class), org.mockito.ArgumentMatchers.eq("新增一门课程")))
                .thenThrow(new AgentPlanningException(
                        "无法可靠判断请求类型：模型返回格式错误",
                        null,
                        "请求类型校验已达到最大尝试次数（2 次）：模型返回的请求类型格式不正确。",
                        List.of("模型返回格式错误"),
                        2
                ));
        AtomicInteger idSequence = new AtomicInteger(200);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        AgentChatResponse response = service.chat(1, chatRequest("新增一门课程"));

        assertEquals("请求类型校验已达到最大尝试次数（2 次）：模型返回的请求类型格式不正确。",
                response.getContent());
        assertEquals(200, response.getUserMessageId());
        assertEquals(201, response.getAssistantMessageId());
        verify(unifiedAgentPlanner).plan(any(AgentDefinition.class), org.mockito.ArgumentMatchers.eq("新增一门课程"));
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatPersistsClarificationDraftReturnedByUnifiedPlanner() throws Exception {
        AgentChatService service = newService(new AgentChatProperties(), unifiedAgentPlanner);
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        AgentActionClarificationDraft draft = courseClarificationDraft();
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(unifiedAgentPlanner.plan(agent, "建一门课，名字叫测试"))
                .thenReturn(AgentPlanningOutcome.write(new AgentDirectActionReply(
                        "请补充：课程分类。",
                        null,
                        draft
                )));
        stubInsertedMessageIds(300);

        AgentChatResponse response = service.chat(1, chatRequest("建一门课，名字叫测试"));

        assertEquals("请补充：课程分类。", response.getContent());
        assertNull(response.getPendingAction());
        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(messageCaptor.capture());
        AgentMessage storedAssistant = messageCaptor.getAllValues().get(1);
        assertEquals("assistant", storedAssistant.getRole());
        assertEquals("action_clarification", storedAssistant.getToolName());
        AgentActionClarificationDraft storedDraft = new ObjectMapper().readValue(
                storedAssistant.getToolResult(), AgentActionClarificationDraft.class);
        assertEquals("course.create", storedDraft.actionCode());
        assertEquals("测试", storedDraft.normalizedArguments().get("title"));
        assertEquals(List.of("categoryId 或 categoryName"), storedDraft.missingRequiredFields());
        verifyNoInteractions(chatModelClient);
    }

    @Test
    void chatContinuesClarificationDraftAndReturnsStructuredPendingAction() throws Exception {
        AgentChatService service = newServiceWithPlanners(schemaDrivenAgentPlanner, unifiedAgentPlanner);
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        AgentActionClarificationDraft draft = courseClarificationDraft();
        AgentPendingActionResponse pendingAction = AgentPendingActionResponse.fromToolData(Map.of(
                "actionId", "action-course-1",
                "actionCode", "course.create",
                "confirmationText", "确认创建课程",
                "parameters", Map.of(
                        "title", "测试",
                        "categoryName", "计算机科学与技术"
                )
        ));
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1, "user", "建一门课，名字叫测试"),
                clarificationMessage(2, draft)
        ));
        when(schemaDrivenAgentPlanner.tryContinue(
                org.mockito.ArgumentMatchers.eq(agent),
                any(AgentActionClarificationDraft.class),
                org.mockito.ArgumentMatchers.eq("课程分类计算机科学与技术")
        )).thenReturn(Optional.of(new AgentDirectActionReply("动作待确认", pendingAction)));
        stubInsertedMessageIds(400);

        AgentChatResponse response = service.chat(1, chatRequest("课程分类计算机科学与技术"));

        assertEquals("动作待确认", response.getContent());
        assertEquals("action-course-1", response.getPendingAction().actionId());
        assertEquals("course.create", response.getPendingAction().actionCode());
        assertEquals("测试", response.getPendingAction().parameters().get("title"));
        ArgumentCaptor<AgentActionClarificationDraft> draftCaptor =
                ArgumentCaptor.forClass(AgentActionClarificationDraft.class);
        verify(schemaDrivenAgentPlanner).tryContinue(
                org.mockito.ArgumentMatchers.eq(agent),
                draftCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("课程分类计算机科学与技术")
        );
        assertEquals("course.create", draftCaptor.getValue().actionCode());
        assertEquals("测试", draftCaptor.getValue().normalizedArguments().get("title"));
        verifyNoInteractions(unifiedAgentPlanner, chatModelClient);

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(messageCaptor.capture());
        AgentMessage storedAssistant = messageCaptor.getAllValues().get(1);
        assertEquals("pending_action", storedAssistant.getToolName());
        assertTrue(storedAssistant.getToolResult().contains("\"actionId\":\"action-course-1\""));
        assertTrue(storedAssistant.getToolResult().contains("\"actionCode\":\"course.create\""));
    }

    @Test
    void chatCancelsActiveClarificationDraftWithoutCallingPlannerOrModel() throws Exception {
        AgentChatService service = newServiceWithPlanners(schemaDrivenAgentPlanner, unifiedAgentPlanner);
        loginAs(10);
        AgentActionClarificationDraft draft = courseClarificationDraft();
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1, "user", "建一门课，名字叫测试"),
                clarificationMessage(2, draft)
        ));
        stubInsertedMessageIds(500);

        AgentChatResponse response = service.chat(1, chatRequest("取消操作"));

        assertEquals("已取消本次操作。", response.getContent());
        assertNull(response.getPendingAction());
        verifyNoInteractions(schemaDrivenAgentPlanner, unifiedAgentPlanner, chatModelClient);
        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(messageCaptor.capture());
        AgentMessage storedAssistant = messageCaptor.getAllValues().get(1);
        assertEquals("assistant", storedAssistant.getRole());
        assertNull(storedAssistant.getToolName());
        assertNull(storedAssistant.getToolResult());
    }

    @Test
    void chatBlocksOrdinaryModelFromClaimingPendingActionWasCreated() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(chatResponse(
                "系统已生成待确认操作，请通过待确认操作入口核对并确认。"
        ));
        stubInsertedMessageIds(600);

        AgentChatResponse response = service.chat(1, chatRequest("课程分类计算机科学与技术"));

        assertEquals("本轮未生成待确认动作，请重新说明要执行的操作。", response.getContent());
        assertNull(response.getPendingAction());
        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(messageCaptor.capture());
        AgentMessage storedAssistant = messageCaptor.getAllValues().get(1);
        assertEquals("本轮未生成待确认动作，请重新说明要执行的操作。", storedAssistant.getContent());
        assertNull(storedAssistant.getToolName());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "401 invalid api key|Agent 模型配置异常，请联系管理员。",
            "429 rate limit|当前请求较多，请稍后再试。",
            "request timeout|Agent 响应超时，请重新发送。",
            "connection refused|暂时无法连接模型服务，请稍后再试。",
            "503 service unavailable|模型服务暂时异常，请稍后再试。"
    }, delimiter = '|')
    void chatOnceMapsModelFailureToSpecificUserMessage(String modelError, String expectedMessage) {
        AgentChatService service = newService();
        when(chatModelClient.call(any(Prompt.class))).thenThrow(new RuntimeException(modelError));

        AgentModelCallException exception = assertThrows(AgentModelCallException.class,
                () -> service.chatOnce("你好"));

        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void chatOnceReportsEmptyModelResponseSpecifically() {
        AgentChatService service = newService();
        when(chatModelClient.call(any(Prompt.class))).thenReturn(chatResponse(""));

        AgentModelCallException exception = assertThrows(AgentModelCallException.class,
                () -> service.chatOnce("你好"));

        assertEquals("模型没有返回内容，请重新发送。", exception.getMessage());
    }

    @Test
    void chatPromptKeepsConfiguredRecentHistoryLimit() {
        AgentChatService service = newService(chatProperties(2, 1000));
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1, "user", "old-user"),
                message(2, "assistant", "old-assistant"),
                message(3, "user", "recent-user"),
                message(4, "assistant", "recent-assistant")
        ));
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("current-question"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(captor.capture(), any(java.time.Duration.class));
        List<?> instructions = captor.getValue().getInstructions();
        assertEquals(4, instructions.size());
        assertTrue(captor.getValue().getInstructions().get(0).getContent().startsWith("你是 Java 课程助教。"));
        assertEquals("recent-user", captor.getValue().getInstructions().get(1).getContent());
        assertEquals("recent-assistant", captor.getValue().getInstructions().get(2).getContent());
        assertEquals("current-question", captor.getValue().getInstructions().get(3).getContent());
    }

    @Test
    void chatPromptStopsAtConfiguredHistoryCharacterBudget() {
        AgentChatService service = newService(chatProperties(10, 5));
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1, "user", "old"),
                message(2, "assistant", "too-long"),
                message(3, "user", "xy")
        ));
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("current-question"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModelClient).call(captor.capture(), any(java.time.Duration.class));
        assertEquals(3, captor.getValue().getInstructions().size());
        assertTrue(captor.getValue().getInstructions().get(0).getContent().startsWith("你是 Java 课程助教。"));
        assertEquals("xy", captor.getValue().getInstructions().get(1).getContent());
        assertEquals("current-question", captor.getValue().getInstructions().get(2).getContent());
    }

    @Test
    void studentChatSkipsAgentToolPlanning() {
        AgentChatService service = newService(new AgentChatProperties(), schemaDrivenAgentPlanner, schemaDrivenReadToolPlanner);
        loginAs(10, "student");
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_search\",\"course_create_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(chatModelClient.call(any(Prompt.class), any(java.time.Duration.class))).thenReturn(chatResponse("plain chat"));

        AgentChatResponse response = service.chat(1, chatRequest("解释一下递归"));

        assertEquals("plain chat", response.getContent());
        assertEquals("[]", agent.getEnabledTools());
        verifyNoInteractions(schemaDrivenAgentPlanner, schemaDrivenReadToolPlanner);
    }

    private AgentChatService newService() {
        return newService(new AgentChatProperties());
    }

    private void assertDirectActionReply(AgentChatResponse response) {
        assertEquals("动作待确认", response.getContent());
        assertNotNull(response.getPendingAction());
    }

    private AgentChatService newService(AgentChatProperties chatProperties) {
        return new AgentChatService(chatModelClient, chatProperties, agentDefinitionMapper, sessionMapper, messageMapper,
                new ObjectMapper(), knowledgeContextProvider,
                (SchemaDrivenAgentPlanner) null, (SchemaDrivenReadToolPlanner) null);
    }

    private AgentChatService newService(AgentPendingActionStore store) {
        return new AgentChatService(chatModelClient, new AgentChatProperties(), agentDefinitionMapper,
                sessionMapper, messageMapper, new ObjectMapper(), knowledgeContextProvider,
                null, null, null, store);
    }

    private AgentChatService newService(AgentChatProperties chatProperties,
                                        SchemaDrivenAgentPlanner writePlanner,
                                        SchemaDrivenReadToolPlanner readPlanner) {
        return new AgentChatService(chatModelClient, chatProperties, agentDefinitionMapper, sessionMapper, messageMapper,
                new ObjectMapper(), knowledgeContextProvider, writePlanner, readPlanner);
    }

    private AgentChatService newService(AgentChatProperties chatProperties,
                                        UnifiedAgentPlanner planner) {
        return new AgentChatService(chatModelClient, chatProperties, agentDefinitionMapper, sessionMapper, messageMapper,
                new ObjectMapper(), knowledgeContextProvider, null, null, planner);
    }

    private AgentChatService newServiceWithPlanners(SchemaDrivenAgentPlanner writePlanner,
                                                    UnifiedAgentPlanner planner) {
        return new AgentChatService(chatModelClient, new AgentChatProperties(), agentDefinitionMapper,
                sessionMapper, messageMapper, new ObjectMapper(), knowledgeContextProvider,
                writePlanner, null, planner);
    }

    private AgentActionClarificationDraft courseClarificationDraft() {
        long createdAt = System.currentTimeMillis();
        return new AgentActionClarificationDraft(
                "course.create",
                "建一门课，名字叫测试",
                Map.of("title", "测试"),
                List.of("categoryId 或 categoryName"),
                createdAt,
                createdAt + 300_000
        );
    }

    private AgentMessage clarificationMessage(Integer id,
                                               AgentActionClarificationDraft draft) throws Exception {
        AgentMessage message = message(id, "assistant", "请补充：课程分类。");
        message.setToolName("action_clarification");
        message.setToolResult(new ObjectMapper().writeValueAsString(draft));
        return message;
    }

    private void stubInsertedMessageIds(int firstId) {
        AtomicInteger idSequence = new AtomicInteger(firstId);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));
    }

    private AgentChatProperties chatProperties(int historyMessageLimit, int historyMaxCharacters) {
        AgentChatProperties properties = new AgentChatProperties();
        properties.setHistoryMessageLimit(historyMessageLimit);
        properties.setHistoryMaxCharacters(historyMaxCharacters);
        return properties;
    }

    private void loginAs(Integer userId) {
        loginAs(userId, "teacher");
    }

    private void loginAs(Integer userId, String roleCode) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setRoleCode(roleCode);
        UserContext.set(user);
    }

    private AgentSession activeSession(Integer sessionId, Integer userId) {
        return activeSession(sessionId, userId, 1);
    }

    private AgentSession activeSession(Integer sessionId, Integer userId, Integer agentId) {
        AgentSession session = new AgentSession();
        session.setId(sessionId);
        session.setAgentId(agentId);
        session.setUserId(userId);
        session.setTitle("测试会话");
        session.setStatus(1);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        return session;
    }

    private AgentMessage message(Integer id, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setSessionId(1);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private AgentDefinition agentDefinition(Integer agentId) {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(agentId);
        agent.setCode("java_tutor");
        agent.setName("Java 助教");
        agent.setSystemPrompt("你是 Java 课程助教。");
        agent.setModel("deepseek-test");
        agent.setTemperature(new BigDecimal("0.30"));
        agent.setStatus(1);
        return agent;
    }

    private ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private com.cupk.aiacademy.dto.AgentChatRequest chatRequest(String content) {
        com.cupk.aiacademy.dto.AgentChatRequest request = new com.cupk.aiacademy.dto.AgentChatRequest();
        request.setContent(content);
        return request;
    }
}
