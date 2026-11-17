package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.cupk.aiacademy.rag.ingestion.IngestionJobRepository;
import com.cupk.aiacademy.rag.ingestion.PageAnalysisService;
import com.cupk.aiacademy.rag.ingestion.RagIngestionService;
import com.cupk.aiacademy.rag.ingestion.RagPageExtractor;
import com.cupk.aiacademy.rag.ingestion.RagTextChunker;
import com.cupk.aiacademy.rag.ocr.OcrModelAdapter;
import com.cupk.aiacademy.rag.ocr.OcrModelClient;
import com.cupk.aiacademy.rag.ocr.OcrProvider;
import com.cupk.aiacademy.rag.ocr.OcrRequest;
import com.cupk.aiacademy.rag.ocr.OcrResult;
import com.cupk.aiacademy.rag.ocr.OcrResultNormalizer;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Paid opt-in measurement of the current ingestion and retrieval implementations. */
@EnabledIfEnvironmentVariable(named = "CONTEST_COURSE_RETRIEVAL_CHECK", matches = "true")
class ContestCourseRetrievalMeasurementTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void measuresAllFrozenQuestionsAndRecordsObservedModes() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path evidence = root.resolve("docs/competition/submission-10039/retrieval");
        Path runDirectory = Path.of(setting("CONTEST_MEASUREMENT_OUTPUT", "target/contest-course-retrieval"));
        Files.createDirectories(runDirectory);
        byte[] datasetBytes = Files.readAllBytes(evidence.resolve("dataset.json"));
        JsonNode dataset = json.readTree(datasetBytes);
        JsonNode freeze = json.readTree(Files.readAllBytes(evidence.resolve("freeze.json")));
        assertEquals(freeze.path("datasetSha256").asText(), sha(datasetBytes));
        assertEquals(20, dataset.path("questions").size());
        Path source = Path.of(setting("CONTEST_COURSE_PDF_DIRECTORY", "D:/PPT/人工智能"))
                .resolve(dataset.path("source").path("file").asText());
        byte[] sourceBytes = Files.readAllBytes(source);
        assertEquals(dataset.path("source").path("sha256").asText(), sha(sourceBytes));

        var report = new LinkedHashMap<String, Object>();
        var observations = new ArrayList<Map<String, Object>>();
        report.put("startedAt", Instant.now().toString());
        report.put("status", "RUNNING");
        report.put("datasetSha256", sha(datasetBytes));
        report.put("sourceSha256", sha(sourceBytes));
        report.put("questions", observations);
        report.put("qualityThreshold", null);
        report.put("parameters", Map.of("rrfK", 1, "candidateLimit", 20, "topK", 5));
        report.put("execution", "Current Java ingestion, PDF page analysis, real configured OCR and embedding HTTP, isolated H2 rows, production retrieval. No cached OCR/vector replay.");
        var ocrCalls = new AtomicInteger();
        var embeddingCalls = new AtomicInteger();
        var embeddingInputs = new AtomicInteger();
        var db = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        long start = System.nanoTime();
        try {
            var real = new RagEmbeddingClient(true, setting("RAG_EMBEDDING_BASE_URL"),
                    setting("RAG_EMBEDDING_API_KEY", "RAG_EMBEDDING_APIKEY", "SILICONFLOW_API_KEY"),
                    setting("RAG_EMBEDDING_MODEL"), json, 120) {
                @Override public List<double[]> embed(List<String> input) {
                    embeddingCalls.incrementAndGet();
                    embeddingInputs.addAndGet(input.size());
                    return super.embed(input);
                }
            };
            assertTrue(real.isConfigured(), "Embedding configuration is required for this live measurement");
            assertEquals("Qwen/Qwen3-VL-Embedding-8B", real.modelName());
            report.put("embeddingModel", real.modelName());
            report.put("embeddingFingerprint", real.fingerprint());
            var properties = new RuntimeModelProperties();
            var endpoint = properties.getOcr();
            endpoint.setBaseUrl(setting("OCR_BASE_URL"));
            endpoint.setApiKey(setting("OCR_API_KEY", "OCR_APIKEY", "SILICONFLOW_API_KEY"));
            endpoint.setModel(setting("OCR_MODEL"));
            endpoint.setTimeoutSeconds(120);
            assertEquals("deepseek-ai/DeepSeek-OCR", endpoint.getModel());
            report.put("ocrModel", endpoint.getModel());
            var adapter = new OcrModelAdapter(properties, new OcrModelClient(properties, json),
                    new OcrResultNormalizer());
            OcrProvider countedOcr = new OcrProvider() {
                public String fingerprint() { return adapter.fingerprint(); }
                public OcrResult recognize(OcrRequest request) {
                    int count = ocrCalls.incrementAndGet();
                    System.out.println("Course measurement OCR page " + request.pageNumber() + ", invocation " + count);
                    return adapter.recognize(request);
                }
            };
            var teacher = new AuthUtils.LoginUser();
            teacher.setUserId(1);
            teacher.setRoleCode("teacher");
            var permissions = mock(PermissionService.class);
            when(permissions.resolveActiveUser(1)).thenReturn(teacher);
            var auth = new RagAuthorizationService(permissions, db);
            var files = new RagFileStore(temporary.resolve("originals").toString());
            var indexes = new RagIndexVersionRepository(db);
            var knowledge = new NativeRagService(db, new RagDocumentParser(), real, json, auth, files,
                    new RagSchemaMigrator(db), indexes);
            knowledge.initialize();
            UserContext.set(teacher);
            var ingestion = new RagIngestionService(db, auth, files, new IngestionJobRepository(db),
                    new RagPageExtractor(), new PageAnalysisService(), countedOcr, new RagTextChunker(), indexes, real, json);
            knowledge.configureIngestion(ingestion);
            String baseId = data(knowledge.createKnowledgeBase("10039 chapter2 measurement", "isolated current-version measurement"))
                    .get("id").toString();
            Map<String, Object> uploaded = data(knowledge.uploadFile(baseId, sourceBytes, source.getFileName().toString()));
            String documentId = uploaded.get("documentId").toString();
            assertTrue(ingestion.runNext("contest-measurement"));
            report.put("ingestion", db.queryForMap("SELECT processing_state,page_count,chunk_count,embedding_status,document_version,parse_revision,active_index_version FROM native_rag_document WHERE id=?", documentId));
            report.put("pageCoverage", db.queryForList("SELECT page_no,method,page_status,error_code,needs_review,LENGTH(chosen_text) AS chosen_characters FROM native_rag_page WHERE document_id=? ORDER BY page_no", documentId));
            report.put("indexes", db.queryForList("SELECT document_version,parse_revision,state,dimensions,embedding_model,chunk_revision,parser_revision FROM native_rag_index_version WHERE document_id=?", documentId));
            report.put("indexedPages", db.queryForList("SELECT DISTINCT page_number FROM native_rag_chunk WHERE document_id=? ORDER BY page_number", Integer.class, documentId));
            Files.writeString(runDirectory.resolve("report.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            var keyword = new NativeRagService(db, new RagDocumentParser(),
                    new RagEmbeddingClient(false, "", "", "", json), json, auth, files,
                    new RagSchemaMigrator(db), indexes);
            keyword.initialize();
            for (JsonNode question : dataset.path("questions")) {
                var row = new LinkedHashMap<String, Object>();
                row.put("id", question.path("id").asText());
                row.put("hybrid", observe(knowledge, baseId, question.path("query").asText()));
                row.put("keyword", observe(keyword, baseId, question.path("query").asText()));
                observations.add(row);
                Files.writeString(runDirectory.resolve("report.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
                System.out.println("Course measurement completed question " + question.path("id").asText());
            }
            assertEquals(20, observations.size());
            report.put("status", "MEASUREMENT_COMPLETED");
        } catch (Exception | AssertionError failure) {
            report.put("status", "RUN_FAILED");
            report.put("failureType", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            report.put("finishedAt", Instant.now().toString());
            report.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            report.put("ocrPageInvocations", ocrCalls.get());
            report.put("embeddingFacadeInvocations", embeddingCalls.get());
            report.put("embeddingInputs", embeddingInputs.get());
            Files.writeString(runDirectory.resolve("report.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            UserContext.clear();
            db.execute("DROP ALL OBJECTS");
        }
    }

    private Map<String, Object> observe(NativeRagService knowledge, String baseId, String query) {
        long start = System.nanoTime();
        var observation = new LinkedHashMap<String, Object>();
        try {
            Result result = knowledge.search(new RagSearchRequest(List.of(baseId), query, 5));
            if (!"200".equals(result.getCode())) {
                observation.put("actualMode", "ERROR");
                observation.put("errorCode", result.getCode());
                observation.put("pages", List.of());
            } else {
                var response = (RagSearchResponse) result.getData();
                observation.put("actualMode", response.actualMode());
                observation.put("degradedReason", response.degradedReason());
                observation.put("pages", response.hits().stream().map(RagSearchHit::page).toList());
                observation.put("indexVersions", response.indexVersions());
            }
        } catch (RuntimeException failure) {
            observation.put("actualMode", "ERROR");
            observation.put("errorCode", failure.getClass().getSimpleName());
            observation.put("pages", List.of());
        }
        observation.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return observation;
    }

    private static String setting(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) return value;
        }
        if (names.length == 2 && !names[1].matches("[A-Z0-9_]+")) return names[1];
        return "";
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Result result) {
        assertEquals("200", result.getCode(), result.getMsg());
        return (Map<String, Object>) result.getData();
    }
}
