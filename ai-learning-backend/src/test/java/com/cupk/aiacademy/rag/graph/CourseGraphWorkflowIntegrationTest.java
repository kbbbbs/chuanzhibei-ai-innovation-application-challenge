package com.cupk.aiacademy.rag.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.assistant.UnifiedAssistantService;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.CourseKnowledgeService;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.RagSearchResponse;
import com.cupk.aiacademy.rag.ingestion.RagIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the graph, native ingestion, retrieval and assistant with real course authorization. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:course-graph-workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "runtime.models.generation.base-url=",
            "runtime.models.generation.api-key=",
            "runtime.models.generation.model=",
            "runtime.models.planning.base-url=",
            "runtime.models.planning.api-key=",
            "runtime.models.planning.model=",
            "runtime.models.embedding.enabled=false",
            "rag.embedding.enabled=false",
            "weknora.enabled=false"
        })
@ActiveProfiles("contest-demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseGraphWorkflowIntegrationTest {
    private static final AtomicInteger IDS = new AtomicInteger(91000);
    private static final Path STORAGE = storage();

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry values) {
        values.add("rag.storage-path", STORAGE::toString);
    }

    private static Path storage() {
        try {
            return Files.createTempDirectory("course-graph-workflow-");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Autowired CourseKnowledgeGraphService graphs;
    @Autowired CourseKnowledgeService libraries;
    @Autowired NativeRagService knowledge;
    @Autowired RagIngestionService ingestion;
    @Autowired UnifiedAssistantService assistant;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @MockBean ChatModelClient model;
    @MockBean Neo4jCourseGraphStore graphStorage;
    int course;
    int classroom;
    String mainDocument;
    String neighborDocument;

    @BeforeEach
    void prepare() {
        reset(model);
        Map<String, List<Map<String, Object>>> stored =
                new java.util.concurrent.ConcurrentHashMap<>();
        doAnswer(
                        call -> {
                            stored.put(call.getArgument(0), call.getArgument(1));
                            return null;
                        })
                .when(graphStorage)
                .save(anyString(), anyList());
        when(graphStorage.read(anyString(), anyList()))
                .thenAnswer(
                        call -> {
                            var sources =
                                    call.<List<com.cupk.aiacademy.rag.RagSearchHit>>getArgument(1);
                            var ids =
                                    sources.stream()
                                            .map(com.cupk.aiacademy.rag.RagSearchHit::chunkId)
                                            .collect(java.util.stream.Collectors.toSet());
                            return stored.getOrDefault(call.getArgument(0), List.of()).stream()
                                    .filter(
                                            fragment ->
                                                    ids.contains(
                                                            ((com.cupk.aiacademy.rag.RagSearchHit)
                                                                            fragment.get("source"))
                                                                    .chunkId()))
                                    .toList();
                        });
        course = IDS.incrementAndGet();
        classroom = course;
        db.update(
                "INSERT INTO t_course(id,title,category_id,teacher_id,status,setup_stage)"
                        + " VALUES(?,'图知识库集成验收',1,2,1,'COMPLETED')",
                course);
        db.update(
                "INSERT INTO"
                        + " t_teaching_class(id,course_id,teacher_id,class_name,status,course_open)"
                        + " VALUES(?,?,2,'图谱测试班',1,TRUE)",
                classroom,
                course);
        db.update(
                "INSERT INTO t_class_student(class_id,student_id,status) VALUES(?,3,1)", classroom);
        login(2, "teacher");
        libraries.ensureCourseBase(course);
        mainDocument = upload("梯度下降更新参数。学习率控制梯度下降的步长。", "optimization.txt");
        neighborDocument = upload("学习率是每次参数更新的步长大小。", "step-size.txt");
        upload("备课策略依赖内部评估。", "teacher-only.txt");
        assertEquals(
                "200", knowledge.grantDocument(mainDocument, course, 1, true, false).getCode());
        assertEquals(
                "200", knowledge.grantDocument(neighborDocument, course, 1, true, false).getCode());
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            String input = call.<Prompt>getArgument(0).getContents();
                            if (input.contains("备课策略依赖内部评估"))
                                return answer(
                                        "{\"nodes\":[{\"label\":\"备课策略\",\"description\":\"教师私有描述\"},{\"label\":\"内部评估\"}],\"relations\":[{\"source\":\"备课策略\",\"target\":\"内部评估\",\"label\":\"依赖\"}]}");
                            if (input.contains("学习率控制梯度下降"))
                                return answer(
                                        "{\"nodes\":[{\"label\":\"梯度下降\",\"description\":\"更新参数\"},{\"label\":\"学习率\",\"description\":\"控制步长\"}],\"relations\":[{\"source\":\"学习率\",\"target\":\"梯度下降\",\"label\":\"控制步长\"}]}");
                            return answer(
                                    "{\"nodes\":[{\"label\":\"学习率\",\"description\":\"每次更新的步长大小\"}],\"relations\":[]}");
                        });
        when(model.stream(any(Prompt.class)))
                .thenReturn(
                        reactor.core.publisher.Flux.just(
                                answer(
                                        "{\"sections\":[{\"type\":\"COURSE_MATERIAL\",\"content\":\"学习率"),
                                answer("控制梯度下降的步长。"),
                                answer("[1][2]\"}]}")));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void chapterEvidenceEntersCourseRetrievalAndStillUsesRealStudentPermissions() throws Exception {
        db.update(
                "INSERT INTO t_chapter(id,course_id,title,status) VALUES(?,?,'章节图谱测试',1)",
                course,
                course);
        var documents =
                db.queryForList(
                        "SELECT d.id FROM native_rag_document d JOIN course_knowledge_base k ON"
                            + " k.kb_id=d.kb_id WHERE k.course_id=?",
                        String.class,
                        course);
        graphs.generate(course, "chapter-evidence", course, documents);
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        Map<?, ?> state;
        do {
            state = (Map<?, ?>) graphs.read(course, course).get("job");
            if (state != null && !"RUNNING".equals(state.get("status"))) break;
            Thread.sleep(30);
        } while (System.nanoTime() < deadline);
        assertNotNull(state);
        assertEquals("COMPLETED", state.get("status"), state.toString());
        login(3, "student");
        var result = knowledge.searchForCourse(course, "梯度下降", 3, 5);
        assertEquals("200", result.getCode(), result.getMsg());
        assertFalse(json.valueToTree(result.getData()).path("graphPaths").isEmpty());
        assertFalse(json.writeValueAsString(graphs.read(course, course)).contains("教师私有描述"));
        login(2, "teacher");
        knowledge.revokeDocumentGrant(mainDocument, course, 1);
        login(3, "student");
        assertFalse(json.writeValueAsString(graphs.readForRetrieval(course)).contains("梯度下降"));
        db.update("UPDATE t_teaching_class SET course_open=FALSE WHERE id=?", classroom);
        assertThrows(ForbiddenException.class, () -> graphs.read(course, course));
    }

    @Test
    void generatedGraphUsesRealSourcesAndStudentOnlySeesGrantedClaims() throws Exception {
        generate();
        assertTrue(json.writeValueAsString(graphs.read(course)).contains("备课策略"));
        login(3, "student");
        var visible = graphs.read(course);
        String serialized = json.writeValueAsString(visible);
        assertTrue(serialized.contains("梯度下降"));
        assertFalse(serialized.contains("备课策略"));
        assertFalse(serialized.contains("教师私有描述"));
        assertFalse(Boolean.TRUE.equals(visible.get("canManage")));
    }

    @Test
    void graphRelationsEnterRetrievalAndFinalAssistantWithExactSourceReferences() throws Exception {
        generate();
        login(3, "student");
        var result = knowledge.searchForCourse(course, "梯度下降", 3, 5);
        assertEquals("200", result.getCode());
        var retrieved = json.convertValue(result.getData(), RagSearchResponse.class);
        assertTrue(
                retrieved.hits().stream()
                        .anyMatch(hit -> neighborDocument.equals(hit.documentId())));
        var tree = json.valueToTree(result.getData());
        assertTrue(tree.path("graphPaths").isArray() && !tree.path("graphPaths").isEmpty());
        int session =
                ((Number) assistant.createSession(Map.of("courseId", course)).get("id")).intValue();
        assistant.send(
                session,
                Map.of("requestId", "graph-answer", "content", "解释梯度下降", "taskType", "chat"));
        var completed = awaitAnswer(session, "graph-answer");
        assertEquals("COMPLETED", completed.get("status"), String.valueOf(completed.get("error")));
        var message = json.valueToTree(completed.get("message"));
        assertFalse(message.path("graphPaths").isEmpty());
        assertFalse(message.path("citations").isEmpty());
        login(2, "teacher");
        knowledge.revokeDocumentGrant(mainDocument, course, 1);
        login(3, "student");
        var history = json.valueToTree(assistant.messages(session).getLast());
        assertEquals("RESTRICTED", history.path("status").asText());
        assertTrue(
                history.path("graphPaths").isMissingNode() || history.path("graphPaths").isEmpty());
    }

    @Test
    void revokedSourcesAndClosedClassCannotExposeGraphOrGraphRetrieval() throws Exception {
        generate();
        knowledge.revokeDocumentGrant(mainDocument, course, 1);
        login(3, "student");
        assertFalse(json.writeValueAsString(graphs.read(course)).contains("梯度下降"));
        var data = json.valueToTree(knowledge.searchForCourse(course, "梯度下降", 3, 5).getData());
        assertTrue(data.path("graphPaths").isMissingNode() || data.path("graphPaths").isEmpty());
        db.update("UPDATE t_teaching_class SET course_open=FALSE WHERE id=?", classroom);
        assertThrows(ForbiddenException.class, () -> graphs.read(course));
        login(1, "admin");
        assertThrows(ForbiddenException.class, () -> graphs.read(course));
    }

    @Test
    void unavailableGraphDatabaseKeepsAuthorizedKeywordRetrievalAndReportsTheMissingGraph()
            throws Exception {
        generate();
        login(3, "student");
        when(graphStorage.read(anyString(), anyList()))
                .thenThrow(
                        new com.cupk.aiacademy.rag.RagAccessException("503", "Neo4j unavailable"));
        var response = knowledge.searchForCourse(course, "梯度下降", 3, 5);
        assertEquals("200", response.getCode());
        var found = json.convertValue(response.getData(), RagSearchResponse.class);
        assertFalse(found.hits().isEmpty());
        assertTrue(found.graphPaths().isEmpty());
        assertTrue(found.warning().contains("Neo4j"));
        doAnswer(
                        call -> {
                            db.update(
                                    "UPDATE t_teaching_class SET course_open=FALSE WHERE id=?",
                                    classroom);
                            throw new com.cupk.aiacademy.rag.RagAccessException(
                                    "503", "Neo4j unavailable");
                        })
                .when(graphStorage)
                .read(anyString(), anyList());
        assertEquals("403", knowledge.searchForCourse(course, "梯度下降", 3, 5).getCode());
    }

    private void generate() throws Exception {
        login(2, "teacher");
        graphs.generate(course, UUID.randomUUID().toString());
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        do {
            var view = graphs.read(course);
            Object job = view.get("job");
            if (job instanceof Map<?, ?> state
                    && !List.of("RUNNING", "QUEUED").contains(state.get("status"))) {
                assertEquals("COMPLETED", state.get("status"), String.valueOf(state.get("error")));
                return;
            }
            Thread.sleep(30);
        } while (System.nanoTime() < deadline);
        fail("图谱任务未结束");
    }

    private Map<String, Object> awaitAnswer(int session, String request) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        do {
            var state = assistant.request(session, request);
            if (!"RUNNING".equals(state.get("status"))) return state;
            Thread.sleep(30);
        } while (System.nanoTime() < deadline);
        fail("图谱问答未结束");
        return Map.of();
    }

    private String upload(String content, String name) {
        String base = libraries.baseId(course);
        var uploaded = knowledge.uploadFile(base, content.getBytes(StandardCharsets.UTF_8), name);
        assertEquals("200", uploaded.getCode());
        ingestion.runNext("graph-workflow-test");
        return db.queryForObject(
                "SELECT id FROM native_rag_document WHERE kb_id=? AND file_name=?",
                String.class,
                base,
                name);
    }

    private void login(int id, String role) {
        var user = new AuthUtils.LoginUser();
        user.setUserId(id);
        user.setRoleCode(role);
        UserContext.set(user);
    }

    private ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
