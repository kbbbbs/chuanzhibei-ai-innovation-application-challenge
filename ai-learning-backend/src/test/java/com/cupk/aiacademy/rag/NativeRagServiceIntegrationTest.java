package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class NativeRagServiceIntegrationTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private NativeRagService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void prepare() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:file:" + temp.resolve("db").toString().replace('\\', '/') + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        jdbc = new JdbcTemplate(source);
        service = newService(disabledEmbeddings());
        service.initialize();
        login(1);
    }

    @AfterEach void cleanContext() { UserContext.clear(); }

    @Test void retrievedFormulaExplicitlyRequiresContentVerification() {
        String kb=newBase("Math source");
        data(service.uploadFile(kb,bytes("Loss formula $$L=\\frac{1}{n}\\sum_i x_i^2$$ where n is sample count."),"loss.md"));
        var found=search(kb,"loss formula");
        assertFalse(found.hits().isEmpty());assertTrue(found.warning().contains("公式"));assertTrue(found.warning().contains("核验"));
    }

    @Test
    void markdownIsPersistedChunkedDeduplicatedAndRetrievedWithActualSource() throws Exception {
        String kb = newBase("Course notes");
        String longMarkdown = "# Gradient descent\n" + "Gradient descent updates parameters using the negative gradient.\n".repeat(35);
        Map<String,Object> uploaded = data(service.uploadFile(kb, bytes(longMarkdown), "../../course.md"));
        String document = uploaded.get("id").toString();
        assertEquals("course.md", uploaded.get("file_name"));
        assertEquals("success", uploaded.get("parse_status"));
        assertTrue(((Number)uploaded.get("chunk_count")).intValue() > 1);
        assertEquals(0, ((Number)uploaded.get("page_count")).intValue());
        assertEquals("not_configured", uploaded.get("embedding_status"));
        assertArrayEquals(bytes(longMarkdown), Files.readAllBytes(service.originalFile(document).path()));

        Map<String,Object> duplicate = data(service.uploadFile(kb, bytes(longMarkdown), "renamed.md"));
        assertEquals(document, duplicate.get("id"));
        assertEquals(true, duplicate.get("duplicate"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document", Integer.class));

        service.uploadFile(kb, bytes("Photosynthesis converts sunlight into chemical energy in plants."), "biology.txt");
        RagSearchResponse response = search(kb, "gradient descent");
        assertEquals("bm25", response.mode());
        assertFalse(response.hits().isEmpty());
        var hit = response.hits().getFirst();
        assertEquals(document, hit.documentId());
        assertEquals(kb, hit.kbId());
        assertTrue(hit.content().contains("Gradient descent"));
        assertEquals("[1]", hit.citation());
        assertNull(hit.page(), "Text files have no invented page locations");
        assertEquals(hit.chunkId(), ((RagSearchHit)success(service.chunk(hit.chunkId()))).chunkId());
        assertTrue(search(kb, "nonexistentunicornxyz").hits().isEmpty());
        assertTrue(search(kb, "what the").hits().isEmpty());
        try (var stored = Files.list(temp.resolve("files"))) { assertEquals(2, stored.count()); }
    }

    @Test
    void pdfPageBoundariesRemainTraceableAndNoPageIsInvented() throws Exception {
        String kb = newBase("PDF materials");
        Map<String,Object> doc = data(service.uploadFile(kb, twoPagePdf(), "lecture.pdf"));
        assertEquals(2, ((Number)doc.get("page_count")).intValue());
        var hit = search(kb, "photosynthesis").hits().getFirst();
        assertEquals(2, hit.page());
        assertTrue(hit.content().contains("Photosynthesis"));
        assertFalse(hit.content().contains("Gradient descent"), "Chunks must not merge different PDF pages");
        assertEquals(2, hit.ordinal());
    }

    @Test
    void mixedPdfReportsPagesWithoutIndexedTextRatherThanSilentlyClaimingCompleteParsing() throws Exception {
        String kb = newBase("Partial text PDF");
        byte[] original = twoPagePdf();
        try (PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(original); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(out);
            Map<String,Object> doc = data(service.uploadFile(kb, out.toByteArray(), "mixed.pdf"));
            assertEquals(List.of(3), doc.get("unindexed_pages"));
            assertTrue(doc.get("parse_warning").toString().contains("未纳入有效索引"));
            assertFalse(doc.get("parse_warning").toString().contains("未实现OCR"));
            assertEquals(3, ((Number)doc.get("page_count")).intValue());
            assertEquals(2, ((Number)doc.get("chunk_count")).intValue());
        }
    }

    @Test
    void privateOwnershipIsEnforcedEvenWhenTheLegacySharedFlagIsSet() {
        String kb = newBase("Private");
        String document = data(service.uploadFile(kb, bytes("Gradient descent is an optimization algorithm."), "note.md")).get("id").toString();
        String chunk = search(kb, "gradient").hits().getFirst().chunkId();
        login(2);
        assertEquals("403", service.search(new RagSearchRequest(List.of(kb), "gradient", 3)).getCode());
        assertEquals("403", service.getDocument(document).getCode());
        assertEquals("403", service.chunk(chunk).getCode());
        assertEquals("403", service.deleteDocument(document).getCode());
        assertThrows(RagAccessException.class, () -> service.originalFile(document));
        assertTrue(((List<?>)success(service.listKnowledgeBases())).isEmpty());

        login(1);
        assertEquals("200", service.updateKnowledgeBase(kb, null, null, true).getCode());
        login(2);
        assertEquals("403",service.search(new RagSearchRequest(List.of(kb),"gradient",3)).getCode());
        assertEquals("403", service.contentForOwner(document, 2).getCode());
        assertTrue(((List<?>)success(service.listKnowledgeBases())).isEmpty());
        assertEquals("403", service.uploadFile(kb, bytes("another document"), "other.txt").getCode());
        assertEquals("403", service.updateKnowledgeBase(kb, "hijacked", null, false).getCode());
        login(1);
        service.updateKnowledgeBase(kb, null, null, false);
        login(2);
        assertEquals("403", service.searchForOwner(List.of(kb), "gradient", 2, 3).getCode());
        UserContext.clear();
        assertEquals("401", service.listKnowledge(kb, 10, 0).getCode());
        assertEquals("401", service.searchForOwner(List.of(kb), "gradient", null, 3).getCode());
    }

    @Test
    void aNewServiceReadsExistingDataAndDeletionRemovesThePersistentIndex() {
        String kb = newBase("Persistent");
        String document = data(service.uploadFile(kb, bytes("Gradient descent minimizes training loss."), "note.txt")).get("id").toString();
        Path original = service.originalFile(document).path();
        service = newService(disabledEmbeddings());
        service.initialize();
        assertEquals(1, ((List<?>)success(service.listKnowledge(kb, 10, 0))).size());
        assertFalse(search(kb, "gradient").hits().isEmpty());
        assertEquals("200", service.deleteDocument(document).getCode());
        assertFalse(Files.exists(original));
        assertTrue(search(kb, "gradient").hits().isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk", Integer.class));
        assertEquals("404", service.getDocument(document).getCode());
        assertEquals("200", service.deleteKnowledgeBase(kb).getCode());
        assertTrue(((List<?>)success(service.listKnowledgeBases())).isEmpty());
    }

    @Test
    void originalSourceChecksumRejectsChangedFileInsteadOfPresentingItAsTheIndexedEvidence() throws Exception {
        String kb = newBase("Source integrity");
        Map<String,Object> doc = data(service.uploadFile(kb, bytes("Gradient descent."), "source.txt"));
        String id = doc.get("id").toString();
        assertEquals(64, doc.get("sha256").toString().length());
        Path source = service.originalFile(id).path();
        Files.writeString(source, "Changed evidence.");
        RagAccessException failure = assertThrows(RagAccessException.class, () -> service.originalFile(id));
        assertEquals("409", failure.code());
    }

    @Test
    void malformedEmptyAndUnsupportedFilesLeaveNoPartialDocumentOrIndex() throws Exception {
        String kb = newBase("Failures");
        assertEquals("400", service.uploadFile(kb, bytes("not a PDF"), "broken.pdf").getCode());
        assertEquals("400", service.uploadFile(kb, bytes("  \n"), "empty.md").getCode());
        assertEquals("400", service.uploadFile(kb, bytes("binary"), "bad.exe").getCode());
        assertEquals("400", service.uploadFile(kb, new byte[RagDocumentParser.MAX_BYTES + 1], "huge.txt").getCode());
        assertEquals("400", service.uploadFile(kb, bytes("x".repeat(RagDocumentParser.MAX_TEXT + 1)), "too-long.md").getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk", Integer.class));
        try (var files = Files.list(temp.resolve("files"))) { assertEquals(0, files.count()); }
    }

    @Test
    void midIndexDatabaseFailureRollsBackAllChunksMetadataAndOriginalFile() throws Exception {
        String kb = newBase("Atomic index");
        // Let the first chunk insert succeed and force the second to fail.
        jdbc.execute("ALTER TABLE native_rag_chunk ADD CONSTRAINT test_one_chunk CHECK (ordinal_number <= 1)");
        Result result = service.uploadFile(kb, bytes("Gradient descent is useful.\n".repeat(90)), "multi.md");
        assertEquals("500", result.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk", Integer.class));
        try (var files = Files.list(temp.resolve("files"))) { assertEquals(0, files.count()); }
    }

    @Test
    void providerFailureIsExplicitlyLexicalAndDoesNotPretendToBeSemanticRetrieval() {
        RagEmbeddingClient failing = mock(RagEmbeddingClient.class);
        when(failing.isConfigured()).thenReturn(true);
        when(failing.isRequested()).thenReturn(true);
        when(failing.fingerprint()).thenReturn("test-model-fingerprint");
        when(failing.embed(anyList())).thenThrow(new IllegalStateException("Simulated unavailable provider"));
        service = newService(failing);
        String kb = newBase("Explicit fallback");
        Map<String,Object> doc = data(service.uploadFile(kb, bytes("Gradient descent training."), "note.txt"));
        assertEquals("provider_unavailable", doc.get("embedding_status"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk WHERE vector_json IS NOT NULL", Integer.class));
        RagSearchResponse response = search(kb, "gradient");
        assertEquals("bm25", response.mode());
        assertFalse(response.warning().isBlank());
        assertFalse(response.hits().isEmpty());
        // This test exercises failure handling, not semantic-model quality.
    }

    @Test
    void native4096VectorLargerThanMySqlTextRoundTripsThroughPersistenceAndRetrieval() throws Exception {
        double[] vector = new double[4096];
        double squaredNorm = 0;
        for (int i = 0; i < vector.length; i++) {
            vector[i] = Math.sin(i + 1.0) / (i + 2.0);
            squaredNorm += vector[i] * vector[i];
        }
        double norm = Math.sqrt(squaredNorm);
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        assertTrue(mapper.writeValueAsBytes(vector).length > 65535,
                "This fixture must expose the MySQL TEXT byte-capacity boundary");
        RagEmbeddingClient embeddings = mock(RagEmbeddingClient.class);
        when(embeddings.isConfigured()).thenReturn(true);
        when(embeddings.isRequested()).thenReturn(true);
        when(embeddings.fingerprint()).thenReturn("synthetic-qwen-vl-4096");
        when(embeddings.modelName()).thenReturn("Qwen/Qwen3-VL-Embedding-8B");
        when(embeddings.embed(anyList())).thenAnswer(invocation -> {
            List<?> input = invocation.getArgument(0);
            return input.stream().map(ignored -> vector.clone()).toList();
        });
        service = newService(embeddings);
        String kb = newBase("Full dimension vectors");
        Map<String,Object> document = data(service.uploadFile(kb,
                bytes("Gradient descent updates parameters to minimize loss."), "native-vector.txt"));
        assertEquals("indexed", document.get("embedding_status"));
        String stored = jdbc.queryForObject("SELECT vector_json FROM native_rag_chunk WHERE document_id=?",
                String.class, document.get("id"));
        assertNotNull(stored);
        assertTrue(stored.getBytes(StandardCharsets.UTF_8).length > 65535);
        assertArrayEquals(vector, mapper.readValue(stored, double[].class), 1e-15);
        RagSearchResponse response = search(kb, "gradient descent");
        assertEquals("hybrid", response.mode());
        assertEquals(document.get("id"), response.hits().getFirst().documentId());
        // Synthetic vectors validate storage and routing; they do not measure model relevance.
    }

    @Test
    void frontendSearchAliasesDeserializeToTheSameContract() throws Exception {
        String kb = newBase("Alias");
        RagSearchRequest request = mapper.readValue("{\"knowledgeBaseIds\":[\"" + kb + "\"],\"query\":\"gradient\",\"topK\":7}", RagSearchRequest.class);
        assertEquals(List.of(kb), request.kbIds());
        assertEquals(7, request.limit());
    }

    private NativeRagService newService(RagEmbeddingClient client) {
        return new NativeRagService(jdbc, new RagDocumentParser(), client, mapper, temp.resolve("files").toString());
    }
    private RagEmbeddingClient disabledEmbeddings() { return new RagEmbeddingClient(false, "", "", "", mapper); }
    private String newBase(String name) { return data(service.createKnowledgeBase(name, "test notes")).get("id").toString(); }
    private RagSearchResponse search(String kb, String query) { return (RagSearchResponse)success(service.search(new RagSearchRequest(List.of(kb), query, 6))); }
    @SuppressWarnings("unchecked") private Map<String,Object> data(Result result) { return (Map<String,Object>)success(result); }
    private Object success(Result result) { assertEquals("200", result.getCode(), result.getMsg()); return result.getData(); }
    private byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private void login(int id) { AuthUtils.LoginUser user = new AuthUtils.LoginUser(); user.setUserId(id); user.setRoleCode("teacher"); UserContext.set(user); }
    private byte[] twoPagePdf() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String content : List.of("Gradient descent optimizes the training loss.", "Photosynthesis transforms solar energy.")) {
                PDPage page = new PDPage(); pdf.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(40, 700); stream.showText(content); stream.endText();
                }
            }
            pdf.save(out); return out.toByteArray();
        }
    }
}
