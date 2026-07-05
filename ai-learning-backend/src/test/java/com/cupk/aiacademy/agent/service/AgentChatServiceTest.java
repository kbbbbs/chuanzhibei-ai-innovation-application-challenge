package com.cupk.aiacademy.agent.service;

import com.cupk.aiacademy.agent.config.AgentChatProperties;
import com.cupk.aiacademy.agent.exception.AgentModelCallException;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.AgentSessionUpdateRequest;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.mapper.AgentMessageMapper;
import com.cupk.aiacademy.mapper.AgentSessionMapper;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.pojo.AgentMessage;
import com.cupk.aiacademy.pojo.AgentSession;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private OpenAiChatModel chatModel;

    @Mock
    private AgentDefinitionMapper agentDefinitionMapper;

    @Mock
    private AgentSessionMapper sessionMapper;

    @Mock
    private AgentMessageMapper messageMapper;

    @Mock
    private WeKnoraService weKnoraService;

    @Mock
    private AgentToolService agentToolService;

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
    void chatUsesAgentDefinitionPromptAndOptions() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1, "user", "讲一下递归")));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("递归是一种函数调用自身的方式。"))
        )));

        service.chat(1, chatRequest("讲一下递归"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertEquals("你是 Java 课程助教。", prompt.getInstructions().get(0).getContent());
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
        assertEquals("deepseek-test", options.getModel());
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
        when(weKnoraService.search(any())).thenReturn(com.cupk.aiacademy.common.Result.success(Map.of(
                "data", List.of(
                        Map.of("content", "递归是函数调用自身的方式。"),
                        Map.of("text", "递归必须设置终止条件。")
                )
        )));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("什么是递归"));

        verify(weKnoraService).search(argThat(request ->
                request != null
                        && request.getKnowledgeBaseIds().equals(List.of("kb-java", "2"))
                        && "什么是递归".equals(request.getQuery())));
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String systemPrompt = captor.getValue().getInstructions().get(0).getContent();
        assertTrue(systemPrompt.contains("知识库参考资料"));
        assertTrue(systemPrompt.contains("递归是函数调用自身的方式。"));
        assertTrue(systemPrompt.contains("递归必须设置终止条件。"));
        assertEquals("什么是递归", captor.getValue().getInstructions()
                .get(captor.getValue().getInstructions().size() - 1).getContent());
    }

    @Test
    void chatAddsEnabledToolResultToPrompt() {
        AgentChatService service = newService();
        loginAs(10);
        AgentDefinition agent = agentDefinition(2);
        agent.setEnabledTools("[\"course_search\",\"learning_plan\"]");
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agent);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(agentToolService.buildToolContext(
                org.mockito.ArgumentMatchers.eq(List.of("course_search", "learning_plan")),
                org.mockito.ArgumentMatchers.eq("推荐一下 Java 学习计划"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn("[course_search]\n课程ID=1；名称=Java 基础\n[learning_plan]\n建议每天学习 30 分钟");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("推荐一下 Java 学习计划"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String systemPrompt = captor.getValue().getInstructions().get(0).getContent();
        assertTrue(systemPrompt.contains("工具调用结果"));
        assertTrue(systemPrompt.contains("课程ID=1；名称=Java 基础"));
        assertTrue(systemPrompt.contains("建议每天学习 30 分钟"));
    }


    @Test
    void chatRecordsFailureMessageWhenModelCallFails() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1, "user", "讲一下递归")));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("timeout"));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        AgentModelCallException exception = assertThrows(AgentModelCallException.class,
                () -> service.chat(1, chatRequest("讲一下递归")));

        assertEquals("AI 回复失败，请稍后重试。", exception.getMessage());
        assertEquals(100, exception.getUserMessageId());
        assertEquals(101, exception.getAssistantMessageId());
        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("user", captor.getAllValues().get(0).getRole());
        assertEquals("assistant", captor.getAllValues().get(1).getRole());
        assertEquals("AI 回复失败，请稍后重试。", captor.getAllValues().get(1).getContent());
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
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("current-question"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<?> instructions = captor.getValue().getInstructions();
        assertEquals(4, instructions.size());
        assertEquals("你是 Java 课程助教。", captor.getValue().getInstructions().get(0).getContent());
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
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("ok"));

        service.chat(1, chatRequest("current-question"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertEquals(3, captor.getValue().getInstructions().size());
        assertEquals("你是 Java 课程助教。", captor.getValue().getInstructions().get(0).getContent());
        assertEquals("xy", captor.getValue().getInstructions().get(1).getContent());
        assertEquals("current-question", captor.getValue().getInstructions().get(2).getContent());
    }

    @Test
    void streamChatStoresAssistantMessageAfterStreaming() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1, "user", "历史问题")));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                chatResponse("递归"),
                chatResponse("是函数调用自身。")
        ));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        service.streamChat(1, chatRequest("讲一下递归"));

        verify(chatModel).stream(any(Prompt.class));
        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("user", captor.getAllValues().get(0).getRole());
        assertEquals("assistant", captor.getAllValues().get(1).getRole());
        assertEquals("递归是函数调用自身。", captor.getAllValues().get(1).getContent());
    }

    @Test
    void streamChatRecordsFailureMessageWhenModelStreamFails() {
        AgentChatService service = newService();
        loginAs(10);
        when(sessionMapper.selectById(1)).thenReturn(activeSession(1, 10, 2));
        when(agentDefinitionMapper.selectById(2)).thenReturn(agentDefinition(2));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1, "user", "历史问题")));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("timeout")));
        AtomicInteger idSequence = new AtomicInteger(100);
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(idSequence.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AgentMessage.class));

        service.streamChat(1, chatRequest("讲一下递归"));

        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("user", captor.getAllValues().get(0).getRole());
        assertEquals("assistant", captor.getAllValues().get(1).getRole());
        assertEquals("AI 回复失败，请稍后重试。", captor.getAllValues().get(1).getContent());
    }

    private AgentChatService newService() {
        return newService(new AgentChatProperties());
    }

    private AgentChatService newService(AgentChatProperties chatProperties) {
        return new AgentChatService(chatModel, chatProperties, agentDefinitionMapper, sessionMapper, messageMapper,
                Runnable::run, new ObjectMapper(), weKnoraService, agentToolService);
    }

    private AgentChatProperties chatProperties(int historyMessageLimit, int historyMaxCharacters) {
        AgentChatProperties properties = new AgentChatProperties();
        properties.setHistoryMessageLimit(historyMessageLimit);
        properties.setHistoryMaxCharacters(historyMaxCharacters);
        return properties;
    }

    private void loginAs(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setRoleCode("student");
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
