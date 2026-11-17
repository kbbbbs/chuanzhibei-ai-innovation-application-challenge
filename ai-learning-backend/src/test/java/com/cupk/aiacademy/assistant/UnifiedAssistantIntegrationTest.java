package com.cupk.aiacademy.assistant;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.CourseKnowledgeService;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.ingestion.RagIngestionService;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real H2, MyBatis, course permissions and retrieval. Only model generation is controlled. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assistant-integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "runtime.models.generation.base-url=", "runtime.models.generation.api-key=", "runtime.models.generation.model=",
        "runtime.models.planning.base-url=", "runtime.models.planning.api-key=", "runtime.models.planning.model=",
        "runtime.models.embedding.enabled=false", "rag.embedding.enabled=false", "weknora.enabled=false",
        "rag.chat.attach-images=true"
})
@ActiveProfiles("contest-demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnifiedAssistantIntegrationTest {
    private static final AtomicInteger IDS = new AtomicInteger(81000);
    private static final Path STORAGE = privateStorage();
    @DynamicPropertySource static void storage(DynamicPropertyRegistry values) { values.add("rag.storage-path",STORAGE::toString); }
    private static Path privateStorage() {
        try { return Files.createTempDirectory("assistant-integration-"); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    @Autowired UnifiedAssistantService assistant;
    @Autowired UnifiedAssistantSchema schema;
    @Autowired com.cupk.aiacademy.rag.visual.RagVisualService figureService;
    @Autowired com.cupk.aiacademy.rag.visual.RagVisualController figureController;
    @Autowired NativeRagService knowledge;
    @Autowired RagIngestionService ingestion;
    @Autowired CourseKnowledgeService libraries;
    @Autowired JdbcTemplate db;
    @MockBean ChatModelClient model;
    int course;
    int chapter;
    int lesson;
    int classroom;

    @BeforeEach void seed() {
        reset(model);
        course=IDS.incrementAndGet(); chapter=course; lesson=course; classroom=course;
        db.update("INSERT INTO t_course(id,title,category_id,teacher_id,status,setup_stage) VALUES(?,'权限验收课程',1,2,1,'COMPLETED')",course);
        db.update("INSERT INTO t_chapter(id,course_id,title,status) VALUES(?,?,?,1)",chapter,course,"测试章节");
        db.update("INSERT INTO t_lesson(id,chapter_id,title,content,status) VALUES(?,?,?,'梯度是函数变化方向。',1)",lesson,chapter,"梯度");
        db.update("INSERT INTO t_teaching_class(id,course_id,teacher_id,class_name,status,course_open) VALUES(?,?,2,'测试班',1,TRUE)",classroom,course);
        db.update("INSERT INTO t_class_student(class_id,student_id,status) VALUES(?,3,1)",classroom);
        login(2,"teacher"); libraries.ensureCourseBase(course);
        when(model.stream(any(Prompt.class))).thenReturn(reactor.core.publisher.Flux.just(response("{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"可以把梯度理解为变化方向。\"}]}")));
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test
    void platformQueryShowsRealPlannerToolEventsAndStreamsItsAnswer() throws Exception {
        when(model.callPlanning(any(Prompt.class), any(Duration.class))).thenReturn(
                response("{\"decision\":\"READ\",\"code\":\"course_search\",\"confidence\":0.99}"),
                response("{\"decision\":\"TOOL\",\"toolName\":\"course_search\",\"arguments\":{\"query\":\"权限验收课程\",\"maxItems\":10},\"confidence\":0.99}"));
        var tokens = reactor.core.publisher.Sinks.many().unicast().<ChatResponse>onBackpressureBuffer();
        var subscribed = new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenReturn(tokens.asFlux().doOnSubscribe(s -> subscribed.countDown()));
        int session = create();
        assistant.send(session, Map.of("requestId", "tools", "content", "查询我的课程列表"));
        assertTrue(subscribed.await(6, TimeUnit.SECONDS), "platform answer must stream after real tool lookup");
        var state = assistant.request(session, "tools");
        var events = (List<Map<String, Object>>) state.get("events");
        assertTrue(events.stream().anyMatch(e -> "MODEL_REQUEST".equals(e.get("state"))));
        assertTrue(events.stream().anyMatch(e -> "TOOL_SUCCEEDED".equals(e.get("state")) && "course_search".equals(e.get("toolName"))));
        var tool = events.stream().filter(e -> "TOOL_SUCCEEDED".equals(e.get("state"))).findFirst().orElseThrow();
        var summary = (Map<?, ?>) tool.get("result");
        assertEquals(1, ((Number) summary.get("itemCount")).intValue());
        assertEquals(List.of(String.valueOf(course)), summary.get("entityIds"));
        assertFalse(events.toString().contains("权限验收课程"), "timeline must not contain raw tool data or arguments");
        tokens.tryEmitNext(response("已查询到当前课程"));
        long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
        while (System.nanoTime() < deadline) {
            state = assistant.request(session, "tools");
            if (state.get("message") instanceof Map<?, ?> message && String.valueOf(message.get("content")).contains("已查询到")) break;
            Thread.sleep(20);
        }
        assertEquals("RUNNING", state.get("status"));
        assertTrue(state.toString().contains("已查询到当前课程"));
        tokens.tryEmitNext(response("，可继续查看章节。"));
        tokens.tryEmitComplete();
        var completed = await(session, "tools");
        assertEquals("COMPLETED", completed.get("status"));
        assertEquals(completed.get("events"), assistant.messages(session).getLast().get("events"));
        verify(model, never()).call(any(Prompt.class), any(Duration.class));
    }

    @Test
    void stoppedPlatformTimelineRechecksItsOwnScopeAfterAnswerIsDiscarded() throws Exception {
        when(model.callPlanning(any(Prompt.class), any(Duration.class))).thenReturn(
                response("{\"decision\":\"READ\",\"code\":\"course_search\",\"confidence\":0.99}"),
                response("{\"decision\":\"TOOL\",\"toolName\":\"course_search\",\"arguments\":{\"query\":\"权限验收课程\",\"maxItems\":10},\"confidence\":0.99}"));
        var subscribed = new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenReturn(reactor.core.publisher.Flux.<ChatResponse>never().doOnSubscribe(s -> subscribed.countDown()));
        int session = ((Number) assistant.createSession(Map.of()).get("id")).intValue();
        assistant.send(session, Map.of("requestId", "revoke-trace", "content", "查询我的课程列表"));
        assertTrue(subscribed.await(6, TimeUnit.SECONDS));
        var cancelled = assistant.cancel(session, "revoke-trace");
        assertFalse(((List<?>) cancelled.get("events")).isEmpty());
        assertNull(cancelled.get("message"));
        db.update("UPDATE t_course SET teacher_id=1 WHERE id=?", course);
        assertEquals(List.of(), assistant.request(session, "revoke-trace").get("events"));
        assertEquals(List.of(), assistant.messages(session).getLast().get("events"));
    }

    @Test void courseAndLessonConversationWorksWithoutAnyUploadedMaterials() throws Exception {
        login(3,"student");
        int session=create();
        assistant.send(session,Map.of("requestId","first","content","解释梯度"));
        Map<String,Object> completed=await(session,"first");
        assertEquals("COMPLETED",completed.get("status"));
        assertEquals(2,assistant.messages(session).size());
        assertTrue(assistant.messages(session).getLast().get("content").toString().contains("通用知识补充"));
        assistant.send(session,Map.of("requestId","first","content","解释梯度"));
        assertEquals(2,assistant.messages(session).size());
        verify(model,times(1)).stream(any(Prompt.class));
        assertTrue(assistant.sessions().stream().anyMatch(row -> row.get("id").equals(session)));
    }

    @Test
    void revokedGrantClearsPersistentPreviewBeforeAnyFurtherModelTokens() throws Exception {
        String document = upload();
        var tokens =
                reactor.core.publisher.Sinks.many().unicast().<ChatResponse>onBackpressureBuffer();
        when(model.stream(any(Prompt.class))).thenReturn(tokens.asFlux());
        login(3, "student");
        int session = create();
        assistant.send(session, Map.of("requestId", "partial", "content", "解释梯度下降"));
        tokens.tryEmitNext(
                response("{\"sections\":[{\"type\":\"COURSE_MATERIAL\",\"content\":\"来源中的解释"));
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        Map<String, Object> state = Map.of();
        while (System.nanoTime() < deadline) {
            state = assistant.request(session, "partial");
            if (state.get("message") instanceof Map<?, ?> message
                    && String.valueOf(message.get("content")).contains("来源中的解释")) break;
            Thread.sleep(20);
        }
        assertEquals("RUNNING", state.get("status"));
        assertTrue(state.get("message") instanceof Map<?, ?>);
        login(2, "teacher");
        knowledge.revokeDocumentGrant(document, course, 1);
        login(3, "student");
        var restricted = assistant.request(session, "partial");
        assertEquals("RESTRICTED", ((Map<?, ?>) restricted.get("message")).get("status"));
        assertFalse(restricted.toString().contains("来源中的解释"));
        tokens.tryEmitNext(response("不应再展示的文字\"}]}"));
        tokens.tryEmitComplete();
        assertEquals("FAILED", await(session, "partial").get("status"));
        assertNull(
                db.queryForObject(
                        "SELECT result_json FROM assistant_request WHERE session_id=?",
                        String.class,
                        session));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM t_agent_message WHERE session_id=? AND"
                            + " role='assistant'",
                        Integer.class,
                        session));
    }

    @Test void invalidHierarchyAdministrativeRoleAndClosedClassCannotReadCourseChat() {
        login(3,"student");
        assertThrows(ForbiddenException.class,()->assistant.createSession(Map.of("courseId",course,"lessonId",lesson+100000)));
        int session=create();
        login(1,"admin");
        assertThrows(ForbiddenException.class,()->assistant.createSession(Map.of("courseId",course)));
        login(3,"student");
        db.update("UPDATE t_teaching_class SET course_open=FALSE WHERE id=?",classroom);
        assertThrows(ForbiddenException.class,()->assistant.messages(session));
        assertFalse(assistant.sessions().stream().anyMatch(row -> row.get("id").equals(session)));
    }

    @Test void exactDocumentGrantRevocationRedactsPreviouslyGeneratedAnswer() throws Exception {
        String document=upload();
        when(model.stream(any(Prompt.class))).thenReturn(reactor.core.publisher.Flux.just(response("{\"sections\":[{\"type\":\"COURSE_MATERIAL\",\"content\":\"梯度下降沿负梯度更新。[1]\"}]}")));
        login(3,"student"); int session=create();
        assistant.send(session,Map.of("requestId","grounded","content","梯度下降沿什么方向更新参数"));
        assertEquals("COMPLETED",await(session,"grounded").get("status"));
        assertFalse(((List<?>)assistant.messages(session).getLast().get("citations")).isEmpty());
        login(2,"teacher"); assertEquals("200",knowledge.revokeDocumentGrant(document,course,1).getCode());
        login(3,"student");
        var restricted=assistant.messages(session).getLast();
        assertEquals("RESTRICTED",restricted.get("status"));
        assertTrue(((List<?>)restricted.get("citations")).isEmpty());
        assertFalse(restricted.get("content").toString().contains("沿负梯度"));
    }

    @Test void revocationDuringModelCallPreventsAnswerFromBeingSaved() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenAnswer(call->{
            entered.countDown(); assertTrue(release.await(10,TimeUnit.SECONDS));
            return reactor.core.publisher.Flux.just(response("{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"不应写入的回答\"}]}"));
        });
        login(3,"student"); int session=create();
        assistant.send(session,Map.of("requestId","revoked","content","解释梯度"));
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            db.update("UPDATE t_teaching_class SET course_open=FALSE WHERE id=?",classroom);
        } finally { release.countDown(); }
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while (System.nanoTime()<deadline && "RUNNING".equals(db.queryForObject("SELECT status FROM assistant_request WHERE session_id=? AND request_id='revoked'",String.class,session))) Thread.sleep(30);
        assertEquals("FAILED",db.queryForObject("SELECT status FROM assistant_request WHERE session_id=? AND request_id='revoked'",String.class,session));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM t_agent_message WHERE session_id=? AND role='assistant'",Integer.class,session));
    }

    @Test void deletedCourseDoesNotBlockOtherSessionsOrAllowReadingItsHistory() {
        login(2,"teacher");
        int removed=create();
        int retained=((Number)assistant.createSession(Map.of("title","保留的通用对话")).get("id")).intValue();
        db.update("INSERT INTO t_agent_message(session_id,role,content,assistant_payload) VALUES(?,'assistant','已删除课程的旧统计',?)",retained,"{\"status\":\"PLATFORM\",\"platformCourses\":["+course+"],\"citations\":[]}");
        db.update("DELETE FROM t_course WHERE id=?",course);
        var visible=assertDoesNotThrow(assistant::sessions);
        assertTrue(visible.stream().anyMatch(row->row.get("id").equals(retained)));
        assertFalse(visible.stream().anyMatch(row->row.get("id").equals(removed)));
        assertEquals("RESTRICTED",assistant.messages(retained).getLast().get("status"));
        assertFalse(assistant.messages(retained).getLast().get("content").toString().contains("旧统计"));
        assertThrows(ForbiddenException.class,()->assistant.messages(removed));
        assertEquals(removed,assistant.legacyOwnedSession(Integer.toString(removed)));
        db.update("INSERT INTO assistant_legacy_session(legacy_id,session_id) VALUES(?,?)","removed-"+removed,removed);
        assertEquals(removed,assistant.legacyOwnedSession("removed-"+removed));
        assertDoesNotThrow(()->assistant.deleteSession(removed));
        assertEquals(0,db.queryForObject("SELECT status FROM t_agent_session WHERE id=?",Integer.class,removed));
    }

    @Test void ownerCanDeleteRevokedHistoryAndCancelItsRunningRequest() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenAnswer(call->{
            entered.countDown();release.await(10,TimeUnit.SECONDS);
            return reactor.core.publisher.Flux.just(response("{\"sections\":[{\"type\":\"GENERAL\",\"content\":\"已取消的回答\"}]}"));
        });
        login(2,"teacher");int session=create();
        assistant.send(session,Map.of("requestId","removed","content","解释梯度"));
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            db.update("DELETE FROM t_course WHERE id=?",course);
            login(3,"student");
            assertThrows(ForbiddenException.class,()->assistant.deleteSession(session));
            login(2,"teacher");
            assertDoesNotThrow(()->assistant.deleteSession(session));
        } finally { release.countDown(); }
        assertEquals("CANCELLED",db.queryForObject("SELECT status FROM assistant_request WHERE session_id=? AND request_id='removed'",String.class,session));
        assertEquals(0,db.queryForObject("SELECT status FROM t_agent_session WHERE id=?",Integer.class,session));
    }

    @Test void conversationRoleChangeRemainsDeniedWhenOtherSessionsAreListed() {
        login(2,"teacher");int session=create();
        db.update("UPDATE t_agent_session SET assistant_role='admin' WHERE id=?",session);
        assertFalse(assistant.sessions().stream().anyMatch(row->row.get("id").equals(session)));
        assertThrows(ForbiddenException.class,()->assistant.messages(session));
    }

    @Test void studentFiguresFollowCourseGrantsAndCurrentParseVersion() throws Exception {
        String document = uploadFigure();
        login(3, "student");
        var result = knowledge.searchForCourse(course, "梯度下降", 3, 5);
        assertEquals("200", result.getCode());
        var hit = ((com.cupk.aiacademy.rag.RagSearchResponse) result.getData()).hits().getFirst();
        assertEquals(1, hit.visuals().size(), "course retrieval must include the associated figure");
        var figure = hit.visuals().getFirst();
        var chunk = (com.cupk.aiacademy.rag.RagSearchHit) knowledge.courseChunk(course, hit.chunkId()).getData();
        assertEquals(figure.id(), chunk.visuals().getFirst().id());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(figureController).build();
        String url = "/api/rag/visuals/" + figure.id() + "/image";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).param("courseId", "" + course))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).param("courseId", "" + (course + 10000)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        db.update("UPDATE t_teaching_class SET course_open=FALSE WHERE id=?", classroom);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).param("courseId", "" + course))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        db.update("UPDATE t_teaching_class SET course_open=TRUE WHERE id=?", classroom);
        db.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP WHERE document_id=?", document);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).param("courseId", "" + course))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        db.update("UPDATE native_rag_course_grant SET revoked_at=NULL WHERE document_id=?", document);
        db.update("UPDATE native_rag_index_version SET parse_revision=parse_revision+1 WHERE document_id=?", document);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).param("courseId", "" + course))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
    }

    @Test void teacherAndStudentAnswersPersistTheirCitedFiguresAndRecheckHistoryAccess() throws Exception {
        String document = uploadFigure();
        when(model.stream(any(Prompt.class))).thenReturn(reactor.core.publisher.Flux.just(response(
                "{\"sections\":[{\"type\":\"COURSE_MATERIAL\",\"content\":\"梯度下降沿负梯度方向更新参数[1]。\"}]}")));
        int studentSession = 0;
        for (int actor : List.of(2, 3)) {
            login(actor, actor == 2 ? "teacher" : "student");
            int session = create();
            if (actor == 3) studentSession = session;
            assistant.send(session, Map.of("requestId", "figures", "content", "解释梯度下降"));
            var completed = await(session, "figures");
            assertEquals("COMPLETED", completed.get("status"), completed.toString());
            var message = assistant.messages(session).getLast();
            var citations = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(message.get("citations"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<com.cupk.aiacademy.rag.RagSearchHit>>() {});
            assertEquals("FIGURE", citations.getFirst().visuals().getFirst().kind());
            assertEquals(document, citations.getFirst().visuals().getFirst().documentId());
        }
        verify(model, times(2)).stream(argThat(prompt -> prompt.getInstructions().stream()
                .filter(message -> message instanceof org.springframework.ai.chat.messages.UserMessage)
                .map(message -> (org.springframework.ai.chat.messages.UserMessage) message)
                .anyMatch(message -> !message.getMedia().isEmpty())));
        db.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP WHERE document_id=?", document);
        assertEquals(List.of(), assistant.messages(studentSession).getLast().get("citations"));
    }

    private String uploadFigure() throws Exception {
        String document = upload();
        db.update("UPDATE native_rag_chunk SET page_number=1 WHERE document_id=?", document);
        int revision = db.queryForObject("SELECT parse_revision FROM native_rag_document WHERE id=?", Integer.class, document);
        var pixels = new java.awt.image.BufferedImage(80, 80, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixels, "png", bytes);
        figureService.capture(document, 1, revision, 1, bytes.toByteArray(),
                "梯度下降\n<|ref|>image<|/ref|><|det|>[[100,100,900,900]]<|/det|>\n图1 梯度下降", "梯度下降沿负梯度方向更新参数。");
        return document;
    }

    private int create() { return ((Number)assistant.createSession(Map.of("courseId",course,"lessonId",lesson)).get("id")).intValue(); }
    private Map<String,Object> await(int session,String request) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        Map<String,Object> state;
        do {
            state=assistant.request(session,request);
            if (!"RUNNING".equals(state.get("status"))) return state;
            Thread.sleep(30);
        } while (System.nanoTime()<deadline);
        fail("生成任务未结束"); return Map.of();
    }
    private String upload() throws Exception {
        login(2,"teacher"); String base=libraries.baseId(course);
        var result=knowledge.uploadFile(base,"梯度下降沿负梯度方向更新参数。".getBytes(StandardCharsets.UTF_8),"gradient.txt");
        assertEquals("200",result.getCode());
        ingestion.runNext("assistant-integration");
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while (System.nanoTime()<deadline && db.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE kb_id=? AND active_index_version IS NOT NULL",Integer.class,base)==0) Thread.sleep(100);
        String document=db.queryForObject("SELECT id FROM native_rag_document WHERE kb_id=? AND active_index_version IS NOT NULL",String.class,base);
        assertEquals("200",knowledge.grantDocument(document,course,1,true,false).getCode());
        return document;
    }
    private void login(int id,String role) { var user=new AuthUtils.LoginUser();user.setUserId(id);user.setRoleCode(role);UserContext.set(user); }
    private ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
}
