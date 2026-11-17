package com.cupk.aiacademy.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cupk.aiacademy.agent.action.AgentPendingActionStore;
import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.core.service.AgentChatService;
import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.visual.RagVisualReader;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

import java.util.*;
import java.util.concurrent.*;

class UnifiedAssistantServiceTest {
    JdbcTemplate db;
    UnifiedAssistantService assistant;
    PermissionService permissions;
    ChatModelClient model;
    AgentChatService tools;
    CountDownLatch started, release;

    @BeforeEach
    void setup() {
        var source =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        db = new JdbcTemplate(source);
        var schema = new UnifiedAssistantSchema(db);
        schema.initialize();
        permissions = mock(PermissionService.class);
        model = mock(ChatModelClient.class);
        tools = mock(AgentChatService.class);
        var actor = new AuthUtils.LoginUser();
        actor.setUserId(7);
        actor.setRoleCode("teacher");
        UserContext.set(actor);
        when(permissions.resolveActiveUser(7))
                .thenAnswer(
                        call -> {
                            var active = new AuthUtils.LoginUser();
                            active.setUserId(7);
                            active.setRoleCode("teacher");
                            return active;
                        });
        when(tools.assistantAgentId()).thenReturn(1);
        when(tools.assistantPrompt(anyInt(), anyList()))
                .thenAnswer(
                        call ->
                                new org.springframework.ai.chat.prompt.Prompt(
                                        call
                                                .<java.util.List<
                                                                org.springframework.ai.chat.messages
                                                                        .Message>>
                                                        getArgument(1)));
        assistant =
                new UnifiedAssistantService(
                        db,
                        new ObjectMapper().findAndRegisterModules(),
                        permissions,
                        mock(RagAuthorizationService.class),
                        mock(NativeRagService.class),
                        mock(RagVisualReader.class),
                        model,
                        tools,
                        mock(AgentPendingActionStore.class),
                        new DataSourceTransactionManager(source),
                        schema,
                        false);
        started = new CountDownLatch(1);
        release = new CountDownLatch(1);
        when(model.stream(any()))
                .thenAnswer(
                        call ->
                                reactor.core.publisher.Flux.defer(
                                        () -> {
                                            started.countDown();
                                            try {
                                                release.await(5, TimeUnit.SECONDS);
                                                return reactor.core.publisher.Flux.just(
                                                        response(
                                                                "{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"梯度描述函数变化的方向。\"}]}"));
                                            } catch (InterruptedException interrupted) {
                                                Thread.currentThread().interrupt();
                                                return reactor.core.publisher.Flux.error(
                                                        interrupted);
                                            }
                                        }));
        when(model.call(any(), any()))
                .thenAnswer(
                        call -> {
                            started.countDown();
                            release.await(5, TimeUnit.SECONDS);
                            return new ChatResponse(
                                    List.of(
                                            new Generation(
                                                    new AssistantMessage(
                                                            "{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"梯度描述函数变化的方向。\"}]}"))));
                        });
    }

    @AfterEach
    void cleanup() {
        release.countDown();
        assistant.shutdown();
        UserContext.clear();
    }

    int create() {
        return ((Number) assistant.createSession(Map.of("title", "测试对话")).get("id")).intValue();
    }

    @Test
    void executionTimelineIsVisibleBeforeTokensAndSurvivesCompletionAndHistory() throws Exception {
        int id = create();
        assistant.send(id, Map.of("requestId", "timeline", "content", "解释梯度"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        var pending = assistant.request(id, "timeline");
        assertNotNull(pending.get("events"), "old Agent execution timeline must be restored");
        var events = (List<Map<String, Object>>) pending.get("events");
        assertEquals(List.of("REQUEST_ACCEPTED", "CONTEXT_VALIDATING", "MODEL_RUNNING"),
                events.stream().map(event -> event.get("state")).toList());
        release.countDown();
        var completed = await(id, "timeline");
        var finalEvents = (List<Map<String, Object>>) completed.get("events");
        assertEquals("COMPLETED", finalEvents.getLast().get("state"));
        for (int i = 0; i < finalEvents.size(); i++) {
            assertEquals(i + 1, ((Number) finalEvents.get(i).get("sequence")).intValue());
            assertTrue(((Number) finalEvents.get(i).get("elapsedMillis")).longValue() >= 0);
        }
        assertEquals(finalEvents, assistant.messages(id).getLast().get("events"));
        assertFalse(finalEvents.toString().contains("TOOL_RUNNING"), "general answers do not pretend to call tools");
    }

    @Test
    void cancellingKeepsRealTimelineAndAddsOnlyOneTerminalEvent() throws Exception {
        int id = create();
        assistant.send(id, Map.of("requestId", "timeline", "content", "解释梯度"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        var cancelled = assistant.cancel(id, "timeline");
        var events = (List<Map<String, Object>>) cancelled.get("events");
        assertNotNull(events, "cancelled timelines must remain reviewable");
        assertEquals("CANCELLED", events.getLast().get("state"));
        assertEquals(events, assistant.cancel(id, "timeline").get("events"));
        release.countDown();
        Thread.sleep(100);
        assertEquals(events, assistant.messages(id).getLast().get("events"));
    }

    @Test
    void retryUsesOneRequestAndOneUserMessageAndOrdinaryQuestionSkipsToolPlanner()
            throws Exception {
        int id = create();
        var input = Map.<String, Object>of("requestId", "r1", "content", "解释梯度的含义");
        assistant.send(id, input);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assistant.send(id, input);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM assistant_request", Integer.class));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE role='user'", Integer.class));
        assertThrows(
                BusinessException.class,
                () -> assistant.send(id, Map.of("requestId", "r1", "content", "另一问题")));
        assertThrows(
                BusinessException.class,
                () -> assistant.send(id, Map.of("requestId", "r2", "content", "解释导数")));
        release.countDown();
        var result = await(id, "r1");
        assertEquals("COMPLETED", result.get("status"));
        assertEquals(2, assistant.messages(id).size());
        verify(tools, never()).planAssistantTools(anyInt(), anyString());
    }

    @Test
    void cancellationIsDurableAndLateModelReturnCannotCommit() throws Exception {
        int id = create();
        assistant.send(id, Map.of("requestId", "r1", "content", "解释梯度"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertEquals("CANCELLED", assistant.cancel(id, "r1").get("status"));
        release.countDown();
        Thread.sleep(100);
        assertEquals("CANCELLED", assistant.request(id, "r1").get("status"));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE role='assistant'",
                        Integer.class));
    }

    @Test
    void revokedActorDuringGenerationPreventsFinalContent() throws Exception {
        int id = create();
        assistant.send(id, Map.of("requestId", "r1", "content", "解释梯度"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        when(permissions.resolveActiveUser(7)).thenThrow(new ForbiddenException("账号已停用"));
        release.countDown();
        for (int i = 0;
                i < 100
                        && "RUNNING"
                                .equals(
                                        db.queryForObject(
                                                "SELECT status FROM assistant_request",
                                                String.class));
                i++) Thread.sleep(20);
        assertEquals(
                "FAILED", db.queryForObject("SELECT status FROM assistant_request", String.class));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE role='assistant'",
                        Integer.class));
    }

    @Test
    void oldHistoryIsReadOnlyAndDoesNotExposeUnverifiableAssistantText() {
        int id = create();
        db.update("UPDATE t_agent_session SET assistant_scope='HISTORY' WHERE id=?", id);
        db.update(
                "INSERT INTO t_agent_message(session_id,role,content) VALUES(?,'assistant','old"
                        + " private course content')",
                id);
        var history = assistant.messages(id);
        assertEquals("RESTRICTED", history.getFirst().get("status"));
        assertFalse(history.getFirst().get("content").toString().contains("private"));
        assertThrows(
                BusinessException.class,
                () -> assistant.send(id, Map.of("requestId", "r1", "content", "继续")));
    }

    @Test
    void generalPlatformReplyRechecksTheCourseSnapshotFromBeforeModelCall() throws Exception {
        db.execute("CREATE TABLE t_course(id INT PRIMARY KEY,title VARCHAR(100),teacher_id INT)");
        db.update("INSERT INTO t_course VALUES(129,'人工智能基础',7)");
        doAnswer(
                        call -> {
                            if (db.queryForObject(
                                            "SELECT teacher_id FROM t_course WHERE id=?",
                                            Integer.class,
                                            (Integer) call.getArgument(0))
                                    != 7) throw new ForbiddenException("课程已不属于当前教师");
                            return null;
                        })
                .when(permissions)
                .requireCourseContentRead(129);
        when(tools.planAssistantTools(anyInt(), anyString()))
                .thenReturn(new AgentChatService.AssistantToolStep(null, "当前课程129的进度统计"));
        int id = create();
        assistant.send(id, Map.of("requestId", "race", "content", "查询我的课程列表"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        db.update("UPDATE t_course SET teacher_id=8 WHERE id=129");
        release.countDown();
        assertEquals("FAILED", await(id, "race").get("status"));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE role='assistant'",
                        Integer.class));
    }

    @Test
    void missingPlanningConfigurationExplainsHowToConfigureIt() throws Exception {
        db.execute("CREATE TABLE t_course(id INT PRIMARY KEY,teacher_id INT)");
        when(tools.planAssistantTools(anyInt(), anyString()))
                .thenThrow(
                        new com.cupk.aiacademy.agent.planner.AgentPlanningException(
                                "planning failed",
                                new com.cupk.aiacademy.model.ModelConfigurationException(
                                        "MODEL_NOT_CONFIGURED")));
        int id = create();
        assistant.send(id, Map.of("requestId", "missing-plan", "content", "查询我的课程列表"));
        var result = await(id, "missing-plan");
        assertEquals("FAILED", result.get("status"));
        assertTrue(result.get("error").toString().contains("规划继承生成模型"));
    }

    @Test
    void missingGenerationConfigurationExplainsTheGenerationSetting() throws Exception {
        when(model.stream(any()))
                .thenThrow(
                        new com.cupk.aiacademy.model.ModelConfigurationException(
                                "MODEL_NOT_CONFIGURED"));
        int id = create();
        assistant.send(id, Map.of("requestId", "missing-generation", "content", "解释梯度"));
        var result = await(id, "missing-generation");
        assertEquals("FAILED", result.get("status"));
        assertTrue(result.get("error").toString().contains("生成模型尚未配置"));
    }

    @Test
    void modelTokensBecomeRecoverableVisiblePreviewsBeforeCompletion() throws Exception {
        var tokens =
                reactor.core.publisher.Sinks.many().unicast().<ChatResponse>onBackpressureBuffer();
        var subscribed = new CountDownLatch(1);
        when(model.stream(any()))
                .thenReturn(tokens.asFlux().doOnSubscribe(subscription -> subscribed.countDown()));
        int id = create();
        assistant.send(id, Map.of("requestId", "streamed", "content", "解释梯度"));
        assertTrue(
                subscribed.await(2, TimeUnit.SECONDS),
                "ordinary answers must consume the model stream");
        tokens.tryEmitNext(response("{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"第一段\\n"));
        var first = awaitPreview(id, "第一段");
        assertEquals("RUNNING", first.get("status"));
        assertFalse(first.toString().contains("_streamEvidence"));
        assertFalse(first.toString().contains("_streamPaths"));
        assertFalse(first.toString().contains("sections\""));
        tokens.tryEmitNext(response("第二段\"}]}"));
        var second = awaitPreview(id, "第二段");
        assertTrue(second.toString().contains("第一段"));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE role='assistant'",
                        Integer.class));
        tokens.tryEmitComplete();
        var completed = await(id, "streamed");
        assertEquals("COMPLETED", completed.get("status"));
        var finalMessage = (Map<?, ?>) completed.get("message");
        assertEquals("none", ((Map<?, ?>) finalMessage.get("retrieval")).get("mode"));
        assertEquals("NONE", ((Map<?, ?>) finalMessage.get("retrieval")).get("actualMode"));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE role='assistant'",
                        Integer.class));
        verify(model, never()).call(any(), any());
    }

    private Map<String, Object> awaitPreview(int id, String content) throws Exception {
        Map<String, Object> last = Map.of();
        for (int i = 0; i < 250; i++) {
            var state = assistant.request(id, "streamed");
            last = state;
            if (state.get("message") instanceof Map<?, ?> message
                    && String.valueOf(message.get("content")).contains(content)) return state;
            Thread.sleep(20);
        }
        fail("Visible model preview did not arrive before completion: " + last);
        return Map.of();
    }

    @Test
    void modelFailureClearsAnAlreadyVisiblePreview() throws Exception {
        var tokens =
                reactor.core.publisher.Sinks.many().unicast().<ChatResponse>onBackpressureBuffer();
        when(model.stream(any())).thenReturn(tokens.asFlux());
        int id = create();
        assistant.send(id, Map.of("requestId", "streamed", "content", "解释梯度"));
        tokens.tryEmitNext(response("{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"可见文字"));
        awaitPreview(id, "可见文字");
        tokens.tryEmitError(new IllegalStateException("provider disconnected"));
        var failed = await(id, "streamed");
        assertEquals("FAILED", failed.get("status"));
        assertNull(failed.get("message"));
        assertNull(db.queryForObject("SELECT result_json FROM assistant_request", String.class));
        assertFalse(assistant.messages(id).toString().contains("可见文字"));
    }

    @Test
    void stoppingClearsPreviewAndCannotAppendLateModelText() throws Exception {
        var tokens =
                reactor.core.publisher.Sinks.many().unicast().<ChatResponse>onBackpressureBuffer();
        when(model.stream(any())).thenReturn(tokens.asFlux());
        int id = create();
        assistant.send(id, Map.of("requestId", "streamed", "content", "解释梯度"));
        tokens.tryEmitNext(response("{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"可见文字"));
        awaitPreview(id, "可见文字");
        var cancelled = assistant.cancel(id, "streamed");
        assertEquals("CANCELLED", cancelled.get("status"));
        assertNull(cancelled.get("message"));
        tokens.tryEmitNext(response("之后文字\"}]}"));
        tokens.tryEmitComplete();
        Thread.sleep(100);
        assertEquals("CANCELLED", assistant.request(id, "streamed").get("status"));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE role='assistant'",
                        Integer.class));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private Map<String, Object> await(int id, String request) throws Exception {
        for (int i = 0; i < 200; i++) {
            var result = assistant.request(id, request);
            if (!"RUNNING".equals(result.get("status"))) return result;
            Thread.sleep(20);
        }
        fail("request did not finish");
        return Map.of();
    }
}
