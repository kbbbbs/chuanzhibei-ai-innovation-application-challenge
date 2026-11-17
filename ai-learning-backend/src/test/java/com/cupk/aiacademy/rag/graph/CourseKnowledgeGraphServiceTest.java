package com.cupk.aiacademy.rag.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cupk.aiacademy.agent.core.llm.ChatModelClient;
import com.cupk.aiacademy.agent.core.llm.ChatModelTimeoutException;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

class CourseKnowledgeGraphServiceTest {
    JdbcTemplate db;
    CourseKnowledgeGraphService graph;
    ChatModelClient model;
    Neo4jCourseGraphStore storage;
    Set<String> studentDocuments;

    @BeforeEach
    void setup() {
        db =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:h2:mem:"
                                        + UUID.randomUUID()
                                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        db.execute("CREATE TABLE t_course(id INT PRIMARY KEY,title VARCHAR(100),teacher_id INT)");
        db.execute(
                "CREATE TABLE t_chapter(id INT PRIMARY KEY,course_id INT,title VARCHAR(100),status"
                    + " INT)");
        db.update(
                "INSERT INTO t_chapter VALUES(101,10,'Chapter one',1),(102,10,'Chapter"
                    + " two',1),(201,11,'Other chapter',1)");
        db.execute(
                "CREATE TABLE course_knowledge_base(course_id INT PRIMARY KEY,kb_id"
                        + " VARCHAR(40),archived BOOLEAN)");
        db.execute("CREATE TABLE native_rag_base(id VARCHAR(40) PRIMARY KEY,owner_id INT)");
        db.execute(
                "CREATE TABLE native_rag_document(id VARCHAR(40) PRIMARY KEY,kb_id"
                        + " VARCHAR(40),title VARCHAR(100),document_version INT,parse_revision"
                        + " INT,active_index_version VARCHAR(40),processing_state VARCHAR(40))");
        db.execute(
                "CREATE TABLE native_rag_index_version(id VARCHAR(40) PRIMARY KEY,document_id"
                    + " VARCHAR(40),state VARCHAR(40),document_version INT,parse_revision INT)");
        db.execute(
                "CREATE TABLE native_rag_chunk(id VARCHAR(40) PRIMARY KEY,document_id"
                    + " VARCHAR(40),kb_id VARCHAR(40),content TEXT,page_number INT,ordinal_number"
                    + " INT,index_version VARCHAR(40),document_version INT,parse_revision INT)");
        db.update("INSERT INTO t_course VALUES(10,'Test course',7),(11,'Other course',7)");
        db.update("INSERT INTO course_knowledge_base VALUES(10,'base',FALSE),(11,'other',FALSE)");
        db.update("INSERT INTO native_rag_base VALUES('base',7),('other',7)");
        var permissions = mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt()))
                .thenAnswer(
                        call -> {
                            int id = call.getArgument(0);
                            var actor = new AuthUtils.LoginUser();
                            actor.setUserId(id);
                            actor.setRoleCode(id == 7 ? "teacher" : id == 8 ? "student" : "admin");
                            return actor;
                        });
        doAnswer(
                        call -> {
                            if ("admin".equals(UserContext.getCurrentRoleCode()))
                                throw new ForbiddenException("denied");
                            return null;
                        })
                .when(permissions)
                .requireCourseContentRead(anyInt());
        var auth = mock(RagAuthorizationService.class);
        when(auth.currentActor())
                .thenAnswer(
                        call ->
                                new RagActor(
                                        UserContext.getCurrentUserId(),
                                        UserContext.getCurrentRoleCode()));
        studentDocuments = ConcurrentHashMap.newKeySet();
        doAnswer(
                        call -> {
                            if (!studentDocuments.contains(call.getArgument(2)))
                                throw new RagAccessException("403", "not granted");
                            return null;
                        })
                .when(auth)
                .requireCourseDocumentRead(any(), anyInt(), anyString(), anyInt(), anyBoolean());
        var libraries = mock(CourseKnowledgeService.class);
        when(libraries.baseId(10)).thenReturn("base");
        model = mock(ChatModelClient.class);
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenReturn(
                        response(
                                "{\"nodes\":[{\"label\":\"Alpha\",\"description\":\"source"
                                    + " explanation\"},{\"label\":\"Beta\"}],\"relations\":[{\"source\":\"Alpha\",\"target\":\"Beta\",\"label\":\"supports\"}]}"));
        storage = mock(Neo4jCourseGraphStore.class);
        Map<String, List<Map<String, Object>>> stored = new ConcurrentHashMap<>();
        doAnswer(
                        call -> {
                            stored.put(call.getArgument(0), call.getArgument(1));
                            return null;
                        })
                .when(storage)
                .save(anyString(), anyList());
        when(storage.read(anyString(), anyList()))
                .thenAnswer(
                        call -> {
                            Set<String> allowed = new HashSet<>();
                            call.<List<RagSearchHit>>getArgument(1)
                                    .forEach(hit -> allowed.add(hit.chunkId()));
                            return stored.getOrDefault(call.getArgument(0), List.of()).stream()
                                    .filter(
                                            fragment ->
                                                    allowed.contains(
                                                            new ObjectMapper()
                                                                    .convertValue(
                                                                            fragment.get("source"),
                                                                            RagSearchHit.class)
                                                                    .chunkId()))
                                    .toList();
                        });
        graph =
                new CourseKnowledgeGraphService(
                        db, new ObjectMapper(), permissions, auth, libraries, model, storage);
        graph.initialize();
        login(7, "teacher");
    }

    @AfterEach
    void cleanup() {
        graph.shutdown();
        UserContext.clear();
    }

    @Test
    void aFailedFragmentDoesNotDiscardOtherLanesAndConcurrencyStaysBounded() throws Exception {
        for (int i = 0; i < 6; i++)
            source("doc" + i, "c" + i, "base", "Alpha supports Beta. Marker" + i);
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var maximum = new java.util.concurrent.atomic.AtomicInteger();
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                            try {
                                Thread.sleep(50);
                                if (call.<Prompt>getArgument(0).getContents().contains("Marker2"))
                                    throw new IllegalStateException("one invalid fragment");
                                return response(
                                        "{\"nodes\":[{\"label\":\"Alpha\"}],\"relations\":[]}");
                            } finally {
                                active.decrementAndGet();
                            }
                        });
        graph.generate(10, "independent-failure");
        var result = await(10);
        assertEquals("FAILED", job(result).get("status"));
        assertEquals(5, job(result).get("savedChunks"));
        assertEquals(2, maximum.get());
    }

    @Test
    void controlledLatencyBenchmarkMeasuresParallelAndCachedRuns() throws Exception {
        for (int i = 0; i < 8; i++) source("doc" + i, "c" + i, "base", "Alpha supports Beta.");
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            Thread.sleep(200);
                            return response("{\"nodes\":[{\"label\":\"Alpha\"}],\"relations\":[]}");
                        });
        long started = System.nanoTime();
        for (int i = 0; i < 8; i++) model.call(new Prompt("control"), Duration.ofSeconds(60));
        long serial = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        clearInvocations(model);
        started = System.nanoTime();
        graph.generate(10, "parallel-benchmark");
        assertEquals("COMPLETED", job(await(10)).get("status"));
        long parallel = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        verify(model, times(8)).call(any(Prompt.class), any(Duration.class));
        clearInvocations(model);
        started = System.nanoTime();
        graph.generate(10, "cache-benchmark");
        var cached = await(10);
        long cache = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        verify(model, never()).call(any(Prompt.class), any(Duration.class));
        assertEquals(8, job(cached).get("reusedChunks"));
        System.out.printf(
                "GRAPH_CONTROLLED_BENCHMARK chunks=8 modelDelayMs=200 serialMs=%d parallelMs=%d"
                    + " cachedMs=%d%n",
                serial, parallel, cache);
    }

    @Test
    void chapterGraphsPersistTheirOwnSourcesAndReuseCourseEvidence() throws Exception {
        source("first", "c1", "base", "Alpha supports Beta.");
        source("second", "c2", "base", "Alpha supports Beta.");
        graph.generate(10, "chapter-one", 101, List.of("first"));
        var one = awaitChapter(101);
        assertEquals("COMPLETED", job(one).get("status"));
        assertEquals(1, job(one).get("totalChunks"));
        assertEquals(List.of("first"), one.get("documentIds"));
        assertTrue(list(graph.read(10, 102), "nodes").isEmpty());
        assertTrue(list(graph.read(10), "nodes").isEmpty());
        assertFalse(list(graph.readForRetrieval(10), "nodes").isEmpty());
        graph.generate(10, "chapter-two", 102, List.of("second"));
        awaitChapter(102);
        graph.generate(10, "course-all");
        await(10);
        verify(model, times(2)).call(any(Prompt.class), any(Duration.class));
        assertEquals("COMPLETED", job(graph.read(10, 101)).get("status"));
        assertThrows(
                RuntimeException.class,
                () -> graph.generate(10, "foreign-chapter", 201, List.of("first")));
        assertThrows(
                RuntimeException.class,
                () -> graph.generate(10, "foreign-source", 101, List.of("outside")));
        login(8, "student");
        assertTrue(list(graph.read(10, 101), "nodes").isEmpty());
        assertTrue(list(graph.readForRetrieval(10), "nodes").isEmpty());
        studentDocuments.add("first");
        assertFalse(list(graph.read(10, 101), "nodes").isEmpty());
    }

    private Map<String, Object> awaitChapter(int chapter) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            var result = graph.read(10, chapter);
            if (job(result) != null && !"RUNNING".equals(job(result).get("status"))) return result;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("chapter graph did not finish");
    }

    @Test
    void extractionUsesTwoLanesAndShowsSavedProgressBeforeCompletion() throws Exception {
        source("first", "c1", "base", "Alpha supports Beta. FirstMarker");
        source("second", "c2", "base", "Alpha supports Beta. SecondMarker");
        CountDownLatch both = new CountDownLatch(2), release = new CountDownLatch(1);
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            both.countDown();
                            assertTrue(
                                    both.await(2, TimeUnit.SECONDS),
                                    "two extractions must overlap");
                            if (call.<Prompt>getArgument(0).getContents().contains("SecondMarker"))
                                release.await(5, TimeUnit.SECONDS);
                            return response("{\"nodes\":[{\"label\":\"Alpha\"}],\"relations\":[]}");
                        });
        graph.generate(10, "two-lanes");
        try {
            assertTrue(both.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (job(graph.read(10)).get("savedChunks").equals(0) && System.nanoTime() < deadline)
                Thread.sleep(10);
            var partial = graph.read(10);
            assertEquals("RUNNING", job(partial).get("status"));
            assertEquals(true, partial.get("partial"));
            assertEquals(1, list(partial, "nodes").size());
        } finally {
            release.countDown();
        }
        assertEquals("COMPLETED", job(await(10)).get("status"));
    }

    @Test
    void completedExtractionIsReusedUntilSourceContentOrModelChanges() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        graph.generate(10, "cached-first");
        await(10);
        graph.generate(10, "cached-second");
        await(10);
        verify(model, times(1)).call(any(Prompt.class), any(Duration.class));
        db.update(
                "UPDATE native_rag_chunk SET content='Alpha supports Beta. Changed' WHERE"
                    + " id='chunk'");
        graph.generate(10, "content-changed");
        await(10);
        verify(model, times(2)).call(any(Prompt.class), any(Duration.class));
        when(model.configuredModel()).thenReturn("different-model");
        graph.generate(10, "model-changed");
        await(10);
        verify(model, times(3)).call(any(Prompt.class), any(Duration.class));
    }

    @Test
    void generationIsDurableIdempotentAndUsesOnlyThisCoursesReadyEvidence() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        source("otherDoc", "otherChunk", "other", "Other secret concepts.");
        graph.generate(10, "request-one");
        var ready = await(10);
        assertEquals("READY", ready.get("status"));
        assertEquals(2, list(ready, "nodes").size());
        assertEquals(1, list(ready, "relations").size());
        var node = list(ready, "nodes").getFirst();
        assertFalse(((List<?>) node.get("citations")).isEmpty());
        graph.generate(10, "request-one");
        verify(model, times(1)).call(any(Prompt.class), any(Duration.class));
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM course_graph_snapshot", Integer.class));
        assertEquals(
                "[]",
                db.queryForObject(
                        "SELECT graph_json FROM course_graph_snapshot WHERE course_id=10",
                        String.class));
        assertNotNull(
                db.queryForObject(
                        "SELECT graph_ref FROM course_graph_snapshot WHERE course_id=10",
                        String.class));
    }

    @Test
    void studentClaimsAreFilteredBeforeSameNamedConceptsAreMerged() throws Exception {
        source("private", "c1", "base", "Alpha supports Beta. private");
        source("public", "c2", "base", "Alpha supports Beta. public");
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            String text = call.<Prompt>getArgument(0).getContents();
                            String description =
                                    text.contains("private")
                                            ? "private explanation"
                                            : "public explanation";
                            return response(
                                    "{\"nodes\":[{\"label\":\"Alpha\",\"description\":\""
                                            + description
                                            + "\"},{\"label\":\"Beta\"}],\"relations\":[{\"source\":\"Alpha\",\"target\":\"Beta\",\"label\":\"supports\"}]}");
                        });
        graph.generate(10, "privacy");
        await(10);
        studentDocuments.add("public");
        login(8, "student");
        var view = graph.read(10);
        assertFalse(view.get("canManage").equals(true));
        assertFalse(new ObjectMapper().writeValueAsString(view).contains("private explanation"));
        assertTrue(new ObjectMapper().writeValueAsString(view).contains("public explanation"));
        studentDocuments.clear();
        assertTrue(list(graph.read(10), "nodes").isEmpty());
        assertThrows(ForbiddenException.class, () -> graph.generate(10, "student-write"));
    }

    @Test
    void deletedOrReindexedSourcesDisappearFromTheReadGraph() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        graph.generate(10, "source-version");
        await(10);
        db.update(
                "UPDATE native_rag_document SET active_index_version='replacement' WHERE id='doc'");
        var view = graph.read(10);
        assertTrue(list(view, "nodes").isEmpty());
        assertEquals(true, view.get("stale"));
    }

    @Test
    void equivalentVersionObjectsDoNotBecomeStaleWhenJsonKeyOrderChanges() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        graph.generate(10, "ordered-source");
        await(10);
        String stored =
                db.queryForObject(
                        "SELECT sources_json FROM course_graph_snapshot WHERE course_id=10",
                        String.class);
        var mapper = new ObjectMapper();
        var values =
                mapper.readValue(
                        stored,
                        new com.fasterxml.jackson.core.type.TypeReference<
                                List<LinkedHashMap<String, Object>>>() {});
        List<String> keys = new ArrayList<>(values.getFirst().keySet());
        Collections.reverse(keys);
        Map<String, Object> reversed = new LinkedHashMap<>();
        keys.forEach(key -> reversed.put(key, values.getFirst().get(key)));
        db.update(
                "UPDATE course_graph_snapshot SET sources_json=? WHERE course_id=10",
                mapper.writeValueAsString(List.of(reversed)));
        assertEquals(false, graph.read(10).get("stale"));
    }

    @Test
    void aGrantRevokedDuringReadCannotRemainInTheReturnedGraph() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        graph.generate(10, "read-revoke");
        await(10);
        studentDocuments.add("doc");
        login(8, "student");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        RagAuthorizationService authorization =
                (RagAuthorizationService)
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                graph, "authorization");
        doAnswer(
                        call -> {
                            if (calls.incrementAndGet() > 1)
                                throw new RagAccessException("403", "grant revoked");
                            return null;
                        })
                .when(authorization)
                .requireCourseDocumentRead(any(), eq(10), eq("doc"), eq(1), eq(false));
        assertTrue(list(graph.read(10), "nodes").isEmpty());
    }

    @Test
    void generationCoversEveryReadyChunkAndAllOfItsText() throws Exception {
        source("first", "c1", "base", "Alpha supports Beta.");
        for (int i = 2; i <= 30; i++)
            db.update(
                    "INSERT INTO native_rag_chunk VALUES(?,?,?,'Alpha supports"
                            + " Beta.',1,?,'first-index',1,1)",
                    "c" + i,
                    "first",
                    "base",
                    i);
        db.update(
                "UPDATE native_rag_chunk SET content='Alpha supports Beta. LateFirstDocumentMarker'"
                        + " WHERE id='c30'");
        source(
                "second",
                "last",
                "base",
                "Alpha supports Beta. " + "context ".repeat(400) + "LastDocumentMarker");
        db.update("UPDATE native_rag_document SET processing_state='EMBEDDING' WHERE id='second'");
        graph.generate(10, "coverage");
        var ready = await(10);
        assertEquals(
                "COMPLETED", job(ready).get("status"), String.valueOf(job(ready).get("error")));
        var summary = (Map<?, ?>) ready.get("sourceSummary");
        assertEquals(31, summary.get("availableChunks"));
        assertEquals(31, summary.get("includedChunks"));
        assertEquals(1, summary.get("processingDocuments"));
        assertEquals(2, ((Number) summary.get("includedDocuments")).intValue());
        assertEquals(false, summary.get("truncated"));
        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(31)).call(prompts.capture(), any(Duration.class));
        assertTrue(
                prompts.getAllValues().stream()
                        .anyMatch(prompt -> prompt.getContents().contains("LastDocumentMarker")));
        assertTrue(
                prompts.getAllValues().stream()
                        .anyMatch(
                                prompt ->
                                        prompt.getContents().contains("LateFirstDocumentMarker")));
    }

    @Test
    void aTransientModelTimeoutRetriesTheSameChunk() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenThrow(new ChatModelTimeoutException("timeout", null))
                .thenReturn(response("{\"nodes\":[{\"label\":\"Alpha\"}],\"relations\":[]}"));
        graph.generate(10, "transient-timeout");
        assertEquals("COMPLETED", job(await(10)).get("status"));
        verify(model, times(2)).call(any(Prompt.class), any(Duration.class));
    }

    @Test
    void failedWorkCanResumeAfterServiceRecreationWithoutRepeatingSavedChunks() throws Exception {
        source("first", "c1", "base", "Alpha supports Beta. FirstMarker");
        source("second", "c2", "base", "Alpha supports Beta. SecondMarker");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            calls.incrementAndGet();
                            if (call.<Prompt>getArgument(0).getContents().contains("SecondMarker"))
                                throw new ChatModelTimeoutException("timeout", null);
                            return response("{\"nodes\":[{\"label\":\"Alpha\"}],\"relations\":[]}");
                        });
        graph.generate(10, "partial-failure");
        var failed = job(await(10));
        assertEquals("FAILED", failed.get("status"));
        assertEquals(1, failed.get("savedChunks"));
        assertTrue(failed.get("error").toString().contains("超时"));
        assertFalse(failed.get("error").toString().contains("配置"));
        graph.shutdown();
        graph =
                new CourseKnowledgeGraphService(
                        db,
                        new ObjectMapper(),
                        (PermissionService)
                                org.springframework.test.util.ReflectionTestUtils.getField(
                                        graph, "permissions"),
                        (RagAuthorizationService)
                                org.springframework.test.util.ReflectionTestUtils.getField(
                                        graph, "authorization"),
                        (CourseKnowledgeService)
                                org.springframework.test.util.ReflectionTestUtils.getField(
                                        graph, "libraries"),
                        model,
                        storage);
        graph.initialize();
        clearInvocations(model);
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenReturn(response("{\"nodes\":[{\"label\":\"Beta\"}],\"relations\":[]}"));
        graph.generate(10, "resume-partial");
        var completed = await(10);
        assertEquals("COMPLETED", job(completed).get("status"));
        assertEquals(2, job(completed).get("savedChunks"));
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(1)).call(prompt.capture(), any(Duration.class));
        assertTrue(prompt.getValue().getContents().contains("SecondMarker"));
        assertEquals(2, list(completed, "nodes").size(), completed.toString());
    }

    @Test
    void legacyFailureIsExplainedWithoutClaimingInvalidModelConfiguration() {
        db.update(
                "INSERT INTO"
                    + " course_graph_job(id,course_id,request_id,owner_id,status,error_text,current_chunk,total_chunks)"
                    + " VALUES('old-failure',10,'old-failure',7,'FAILED','图谱生成失败，请检查模型配置后重试',9,24)");
        var old = job(graph.read(10));
        assertEquals(0, old.get("savedChunks"));
        assertFalse(old.get("error").toString().contains("检查模型配置"));
    }

    @Test
    void resumingReextractsAnyCheckpointWhoseParseRevisionChanged() throws Exception {
        source("first", "c1", "base", "Alpha supports Beta.");
        source("second", "c2", "base", "Alpha supports Beta.");
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            if (!call.<Prompt>getArgument(0).getContents().contains("资料：first\n"))
                                throw new IllegalStateException("provider unavailable");
                            return response("{\"nodes\":[{\"label\":\"Alpha\"}],\"relations\":[]}");
                        });
        graph.generate(10, "before-revision-change");
        assertEquals(1, job(await(10)).get("savedChunks"));
        db.update("UPDATE native_rag_document SET parse_revision=2 WHERE id='first'");
        db.update("UPDATE native_rag_index_version SET parse_revision=2 WHERE document_id='first'");
        db.update("UPDATE native_rag_chunk SET parse_revision=2 WHERE document_id='first'");
        clearInvocations(model);
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenReturn(response("{\"nodes\":[{\"label\":\"Beta\"}],\"relations\":[]}"));
        graph.generate(10, "after-revision-change");
        assertEquals("COMPLETED", job(await(10)).get("status"));
        verify(model, times(2)).call(any(Prompt.class), any(Duration.class));
    }

    @Test
    void existingJobTableReceivesTheResumeColumnWithoutLosingItsRows() {
        db.update(
                "INSERT INTO course_graph_job(id,course_id,request_id,owner_id,status)"
                        + " VALUES('legacy',10,'legacy',7,'FAILED')");
        db.execute("ALTER TABLE course_graph_job DROP COLUMN resume_job_id");
        graph.initialize();
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM course_graph_job WHERE id='legacy'", Integer.class));
        assertNull(
                db.queryForObject(
                        "SELECT resume_job_id FROM course_graph_job WHERE id='legacy'",
                        String.class));
    }

    @Test
    void completionDuringGraphIoCannotLabelAnEarlierSnapshotAsTheCompletedTask() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        graph.generate(10, "initial");
        await(10);
        db.update(
                "INSERT INTO course_graph_job(id,course_id,request_id,owner_id,status)"
                        + " VALUES('finishing',10,'finishing',7,'RUNNING')");
        when(storage.read(anyString(), anyList()))
                .thenAnswer(
                        call -> {
                            db.update(
                                    "UPDATE course_graph_job SET status='COMPLETED' WHERE"
                                            + " id='finishing'");
                            return List.of();
                        });
        assertEquals("RUNNING", job(graph.read(10)).get("status"));
    }

    @Test
    void graphDatabaseFailureKeepsInheritedCheckpointsForAnotherResume() throws Exception {
        source("first", "c1", "base", "Alpha supports Beta.");
        source("second", "c2", "base", "Alpha supports Beta.");
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            if (!call.<Prompt>getArgument(0).getContents().contains("资料：first\n"))
                                throw new IllegalStateException("provider unavailable");
                            return response("{\"nodes\":[{\"label\":\"Alpha\"}],\"relations\":[]}");
                        });
        graph.generate(10, "first-attempt");
        assertEquals(1, job(await(10)).get("savedChunks"));
        doThrow(new RagAccessException("503", "Neo4j unavailable")).when(storage).verify();
        graph.generate(10, "database-offline");
        assertEquals(1, job(await(10)).get("savedChunks"));
        doNothing().when(storage).verify();
        clearInvocations(model);
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenReturn(response("{\"nodes\":[{\"label\":\"Beta\"}],\"relations\":[]}"));
        graph.generate(10, "database-restored");
        assertEquals("COMPLETED", job(await(10)).get("status"));
        verify(model, times(1)).call(any(Prompt.class), any(Duration.class));
    }

    @Test
    void failureAndCancellationPreserveThePreviousCompleteSnapshot() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        graph.generate(10, "first");
        await(10);
        when(model.configuredModel()).thenReturn("uncached-model-for-failure-test");
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenThrow(new IllegalStateException("provider down"));
        graph.generate(10, "failure");
        var failed = await(10);
        assertEquals("FAILED", job(failed).get("status"));
        assertEquals(2, list(failed, "nodes").size());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            release.await(5, TimeUnit.SECONDS);
                            return response("{\"nodes\":[],\"relations\":[]}");
                        });
        var pending = graph.generate(10, "cancel");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        graph.cancel(10, job(pending).get("id").toString());
        release.countDown();
        assertEquals("CANCELLED", job(graph.read(10)).get("status"));
        assertEquals(2, list(graph.read(10), "nodes").size());
    }

    @Test
    void emptyAndInventedConceptsDoNotBecomeCourseKnowledge() throws Exception {
        source("doc", "chunk", "base", "Alpha supports Beta.");
        when(model.call(any(Prompt.class), any(Duration.class)))
                .thenReturn(response("{\"nodes\":[{\"label\":\"Invented\"}],\"relations\":[]}"));
        graph.generate(10, "no-concepts");
        assertEquals("EMPTY", await(10).get("status"));
        assertTrue(list(graph.read(10), "nodes").isEmpty());
    }

    @Test
    void restartingMarksRunningJobsInterruptedAndAdministratorCannotReadCourseGraph() {
        db.update(
                "INSERT INTO course_graph_job(id,course_id,request_id,owner_id,status)"
                        + " VALUES('old',10,'old',7,'RUNNING')");
        graph.initialize();
        assertEquals("INTERRUPTED", job(graph.read(10)).get("status"));
        login(1, "admin");
        assertThrows(ForbiddenException.class, () -> graph.read(10));
    }

    private void source(String document, String chunk, String base, String text) {
        db.update(
                "INSERT INTO native_rag_document VALUES(?,?,?,1,1,?,'COMPLETED')",
                document,
                base,
                document,
                document + "-index");
        db.update(
                "INSERT INTO native_rag_index_version VALUES(?,?,'KEYWORD_READY',1,1)",
                document + "-index",
                document);
        db.update(
                "INSERT INTO native_rag_chunk VALUES(?,?,?,?,1,1,?,1,1)",
                chunk,
                document,
                base,
                text,
                document + "-index");
    }

    private Map<String, Object> await(int course) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Map<String, Object> result;
        do {
            result = graph.read(course);
            if (!"RUNNING".equals(job(result).get("status"))) return result;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail("graph generation did not finish");
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> view, String name) {
        return (List<Map<String, Object>>) view.get(name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> job(Map<String, Object> view) {
        return (Map<String, Object>) view.get("job");
    }

    private void login(int id, String role) {
        var actor = new AuthUtils.LoginUser();
        actor.setUserId(id);
        actor.setRoleCode(role);
        UserContext.set(actor);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
