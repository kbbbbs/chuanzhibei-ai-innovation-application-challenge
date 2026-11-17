package com.cupk.aiacademy.rag.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.rag.ingestion.RagIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
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
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real H2 permissions and real authenticated Neo4j. Only the LLM is controlled. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:course-neo-authorization;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "runtime.models.generation.base-url=",
            "runtime.models.generation.api-key=",
            "runtime.models.generation.model=",
            "runtime.models.planning.base-url=",
            "runtime.models.planning.api-key=",
            "runtime.models.planning.model=",
            "runtime.models.embedding.enabled=false",
            "rag.embedding.enabled=false",
            "weknora.enabled=false",
            "rag.graph.enabled=true"
        })
@ActiveProfiles("contest-demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "NEO4J_INTEGRATION_TEST", matches = "true")
class CourseNeo4jAuthorizationIntegrationTest {
    private static final AtomicInteger IDS = new AtomicInteger(92000);
    private static final String STORAGE =
            System.getProperty("java.io.tmpdir") + "/course-neo-auth-" + UUID.randomUUID();

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", () -> System.getenv("NEO4J_TEST_URI"));
        registry.add(
                "spring.neo4j.authentication.password", () -> System.getenv("NEO4J_TEST_PASSWORD"));
        registry.add("rag.storage-path", () -> STORAGE);
    }

    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired CourseKnowledgeService libraries;
    @Autowired NativeRagService knowledge;
    @Autowired RagIngestionService ingestion;
    @Autowired CourseKnowledgeGraphService graphs;
    @Autowired Neo4jGraphRepository repository;
    @MockBean ChatModelClient model;
    int course;
    String publicDocument;

    @BeforeEach
    void prepare() {
        course = IDS.incrementAndGet();
        db.update(
                "INSERT INTO t_course(id,title,category_id,teacher_id,status,setup_stage)"
                    + " VALUES(?,'Neo4j graph verification',1,2,1,'COMPLETED')",
                course);
        db.update(
                "INSERT INTO"
                    + " t_teaching_class(id,course_id,teacher_id,class_name,status,course_open)"
                    + " VALUES(?,?,2,'Graph test',1,TRUE)",
                course,
                course);
        db.update("INSERT INTO t_class_student(class_id,student_id,status) VALUES(?,3,1)", course);
        login(2, "teacher");
        libraries.ensureCourseBase(course);
        publicDocument = upload("Alpha supports Beta. public reference", "public.txt");
        upload("Alpha supports Gamma. private reference", "private.txt");
        assertEquals(
                "200", knowledge.grantDocument(publicDocument, course, 1, true, false).getCode());
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            boolean secret =
                                    call.<Prompt>getArgument(0)
                                            .getContents()
                                            .contains("private reference");
                            String target = secret ? "Gamma" : "Beta",
                                    description =
                                            secret ? "private explanation" : "public explanation";
                            return new ChatResponse(
                                    List.of(
                                            new Generation(
                                                    new AssistantMessage(
                                                            "{\"nodes\":[{\"label\":\"Alpha\",\"description\":\""
                                                                    + description
                                                                    + "\"},{\"label\":\""
                                                                    + target
                                                                    + "\"}],\"relations\":[{\"source\":\"Alpha\",\"target\":\""
                                                                    + target
                                                                    + "\",\"label\":\"supports\"}]}"))));
                        });
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void nativeStorageAndQueriesUseCurrentCourseAndDocumentPermissions() throws Exception {
        graphs.generate(course, UUID.randomUUID().toString());
        var teacher = await();
        String ref = teacher.get("graphRef").toString();
        assertEquals(
                "[]",
                db.queryForObject(
                        "SELECT graph_json FROM course_graph_snapshot WHERE course_id=?",
                        String.class,
                        course));
        assertEquals(3, repository.readAll(ref, 1).nodes().size());
        assertEquals(2, repository.readAll(ref, 1).edges().size());
        login(3, "student");
        var student = graphs.read(course);
        String visible = json.writeValueAsString(student);
        assertFalse(visible.contains("private explanation"));
        assertFalse(visible.contains("Gamma"));
        var nodes = maps(student.get("nodes"));
        String alpha =
                nodes.stream()
                        .filter(node -> "Alpha".equals(node.get("label")))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .toString();
        String beta =
                nodes.stream()
                        .filter(node -> "Beta".equals(node.get("label")))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .toString();
        assertEquals(1, maps(graphs.neighbors(course, alpha, 1, "").get("relations")).size());
        assertEquals(1, maps(graphs.paths(course, alpha, beta).get("relations")).size());
        login(2, "teacher");
        knowledge.revokeDocumentGrant(publicDocument, course, 1);
        login(3, "student");
        assertTrue(maps(graphs.read(course).get("nodes")).isEmpty());
        db.update("UPDATE t_teaching_class SET course_open=FALSE WHERE id=?", course);
        assertThrows(ForbiddenException.class, () -> graphs.read(course));
        login(1, "admin");
        assertThrows(ForbiddenException.class, () -> graphs.read(course));
    }

    @Test
    void legacySqlGraphsMigrateWithoutExposingPrivateClaimsOnStudentRead() throws Exception {
        var hits =
                ((RagSearchResponse) knowledge.searchForCourse(course, "Alpha", 2, 10).getData())
                        .hits();
        List<Map<String, Object>> fragments = new ArrayList<>(), versions = new ArrayList<>();
        for (var hit : hits) {
            boolean secret = !hit.documentId().equals(publicDocument);
            fragments.add(
                    Map.of(
                            "source",
                            hit,
                            "nodes",
                            List.of(
                                    Map.of(
                                            "id",
                                            "alpha",
                                            "label",
                                            "Alpha",
                                            "description",
                                            secret ? "private explanation" : "public explanation")),
                            "relations",
                            List.of()));
            versions.add(
                    Map.of(
                            "chunkId",
                            hit.chunkId(),
                            "documentId",
                            hit.documentId(),
                            "indexVersion",
                            hit.indexVersion(),
                            "documentVersion",
                            hit.documentVersion(),
                            "parseRevision",
                            hit.parseRevision()));
        }
        assertEquals(2, fragments.size());
        String legacy = json.writeValueAsString(fragments);
        db.update(
                "INSERT INTO"
                    + " course_graph_snapshot(course_id,graph_json,sources_json,source_summary_json)"
                    + " VALUES(?,?,?,'{}')",
                course,
                legacy,
                json.writeValueAsString(versions));
        login(3, "student");
        var migrated = graphs.read(course);
        assertEquals("NEO4J", migrated.get("storage"));
        assertNotNull(migrated.get("graphRef"));
        assertEquals(1, repository.readAll(migrated.get("graphRef").toString(), 1).nodes().size());
        assertFalse(json.writeValueAsString(migrated).contains("private explanation"));
        assertTrue(json.writeValueAsString(migrated).contains("public explanation"));
        assertEquals(
                legacy,
                db.queryForObject(
                        "SELECT graph_json FROM course_graph_snapshot WHERE course_id=?",
                        String.class,
                        course));
    }

    private String upload(String text, String name) {
        String base = libraries.baseId(course);
        assertEquals(
                "200",
                knowledge.uploadFile(base, text.getBytes(StandardCharsets.UTF_8), name).getCode());
        ingestion.runNext("neo4j-auth-test");
        return db.queryForObject(
                "SELECT id FROM native_rag_document WHERE kb_id=? AND file_name=?",
                String.class,
                base,
                name);
    }

    private Map<String, Object> await() throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < end) {
            var result = graphs.read(course);
            if (result.get("job") instanceof Map<?, ?> job
                    && !"RUNNING".equals(job.get("status"))) {
                assertEquals("COMPLETED", job.get("status"), String.valueOf(job.get("error")));
                return result;
            }
            Thread.sleep(30);
        }
        fail("Graph task did not finish");
        return Map.of();
    }

    private void login(int id, String role) {
        var user = new AuthUtils.LoginUser();
        user.setUserId(id);
        user.setRoleCode(role);
        UserContext.set(user);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
