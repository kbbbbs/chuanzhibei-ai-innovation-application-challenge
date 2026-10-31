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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic vectors prove disk/JVM persistence only, never real model relevance. */
class K03PersistenceRestartTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String QUERY = "directionalminimumquery";
    static final String MODEL = "Qwen/Qwen3-VL-Embedding-8B";
    static final String FINGERPRINT = sha("synthetic-offline-k03-restart-fixture-v1".getBytes(StandardCharsets.UTF_8));
    static final int DIMENSIONS = 4;
    @TempDir Path temp;

    @AfterEach void clearActor() { UserContext.clear(); }

    @Test void aSecondJvmReadsPersistedVectorsAndOriginalVersionMetadata() throws Exception {
        String jdbcUrl = "jdbc:h2:file:" + temp.resolve("database").toString().replace('\\', '/')
                + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE";
        Path originals = temp.resolve("originals");
        JdbcTemplate jdbc = jdbc(jdbcUrl);
        var embeddings = new SyntheticEmbedding(false);
        var service = new NativeRagService(jdbc, new RagDocumentParser(), embeddings, JSON, originals.toString());
        service.initialize();
        login(301);
        String kb = data(service.createKnowledgeBase("K03 synthetic restart fixture", "Persistence verification only")).get("id").toString();
        byte[] source = syntheticPdf();
        Map<String,Object> uploaded = data(service.uploadFile(kb, source, "synthetic-restart.pdf"));
        String document = uploaded.get("id").toString();
        String oldIndex = uploaded.get("active_index_version").toString();
        Map<String,Object> rebuilt = data(service.reindexDocument(document));
        String activeIndex = rebuilt.get("active_index_version").toString();
        assertNotEquals(oldIndex, activeIndex, "Use the real reindex publication path");
        assertEquals("indexed", rebuilt.get("embedding_status"));
        assertEquals(2, ((Number) rebuilt.get("page_count")).intValue());
        assertEquals(2, embeddings.documentBatches);
        assertEquals(0, embeddings.queryBatches);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version WHERE document_id=?", Integer.class, document));
        var beforeRows = persistedRows(jdbc, document, activeIndex);
        assertEquals(2, beforeRows.size());
        assertEquals(sha(source), sha(Files.readAllBytes(service.originalFile(document).path())));
        var expectedIndex = jdbc.queryForMap("SELECT * FROM native_rag_index_version WHERE id=?", activeIndex);

        login(302);
        String forbiddenKb = data(service.createKnowledgeBase("Other owner fixture", "Authorization boundary")).get("id").toString();
        data(service.uploadFile(forbiddenKb, "Private fixture outside authorized library.".getBytes(StandardCharsets.UTF_8), "private.txt"));
        login(301);
        assertEquals("403", service.search(new RagSearchRequest(List.of(forbiddenKb), QUERY, 5)).getCode());

        Map<String,Object> config = new LinkedHashMap<>();
        config.put("jdbcUrl", jdbcUrl); config.put("originals", originals.toString());
        config.put("owner", 301); config.put("kb", kb); config.put("forbiddenKb", forbiddenKb);
        config.put("document", document); config.put("activeIndex", activeIndex); config.put("oldIndex", oldIndex);
        config.put("sourceSha256", sha(source)); config.put("rows", beforeRows);
        config.put("documentVersion", rebuilt.get("document_version")); config.put("parseRevision", rebuilt.get("parse_revision"));
        config.put("parserRevision", expectedIndex.get("parser_revision")); config.put("chunkRevision", expectedIndex.get("chunk_revision"));
        config.put("rowCount", jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk", Integer.class));
        config.put("indexCount", jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version", Integer.class));
        Path result = temp.resolve("child-result.json");
        config.put("result", result.toString());
        config.put("parentPid", ProcessHandle.current().pid());
        Path privateConfig = temp.resolve("restart-config.json");
        JSON.writeValue(privateConfig.toFile(), config);

        // DriverManagerDataSource does not pool connections. All JdbcTemplate operations above close theirs.
        // SHUTDOWN closes H2's remaining sessions and flushes the file before the second JVM starts.
        jdbc.execute("SHUTDOWN");
        UserContext.clear();
        Path dataFile = temp.resolve("database.mv.db");
        assertTrue(Files.size(dataFile) > 0, "A real disk database must exist before restart");

        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path childLog = temp.resolve("child-status.log");
        var builder = new ProcessBuilder(java.toString(), "-cp", classPath,
                "com.cupk.aiacademy.rag.K03PersistenceRestartProbe", privateConfig.toString());
        // No provider credentials or other private parent environment values enter the child process.
        builder.environment().clear();
        for (String key : List.of("SystemRoot", "WINDIR")) {
            String value = System.getenv(key); if (value != null) builder.environment().put(key, value);
        }
        builder.environment().put("TEMP", temp.toString()); builder.environment().put("TMP", temp.toString());
        builder.redirectErrorStream(true).redirectOutput(childLog.toFile());
        Process child = builder.start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Restart probe exceeded its 30-second deadline");
            assertEquals(0, child.exitValue(), () -> "Restart probe failed: " + read(childLog));
            var report = JSON.readTree(result.toFile());
            assertEquals("PASS", report.path("status").asText());
            assertEquals("SYNTHETIC_PERSISTENCE_ONLY", report.path("evidenceType").asText());
            assertNotEquals(ProcessHandle.current().pid(), report.path("childPid").asLong());
            assertEquals("HYBRID", report.path("actualMode").asText());
            assertEquals(1, report.path("queryEmbeddingBatches").asInt());
            assertEquals(0, report.path("documentEmbeddingBatches").asInt());
            assertEquals(beforeRows.size(), report.path("persistedActiveVectors").asInt());
            assertEquals(sha(source), report.path("sourceSha256").asText());
            assertTrue(report.path("authorizedScopeVerified").asBoolean());
            assertTrue(report.path("diskRowsUnchanged").asBoolean());
            Map<String,Object> finalReport = JSON.convertValue(report, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            finalReport.put("verifiedAt", Instant.now().toString());
            finalReport.put("parentDatabaseShutdownBeforeChild", true);
            finalReport.put("diskDatabasePresentBeforeChild", true);
            Path output = Path.of("target", "k03-restart", "restart-report.json");
            Files.createDirectories(output.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), finalReport);
        } finally {
            if (child.isAlive()) {
                child.destroy();
                if (!child.waitFor(2, TimeUnit.SECONDS)) {
                    child.destroyForcibly();
                    assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Restart probe could not be terminated");
                }
            }
        }
    }

    static JdbcTemplate jdbc(String url) { return new JdbcTemplate(new DriverManagerDataSource(url, "sa", "")); }
    static void login(int id) {
        var actor = new AuthUtils.LoginUser(); actor.setUserId(id); actor.setRoleCode("teacher"); UserContext.set(actor);
    }
    static Object success(Result result) {
        assertEquals("200", result.getCode(), result.getMsg()); return result.getData();
    }
    @SuppressWarnings("unchecked") static Map<String,Object> data(Result result) { return (Map<String,Object>) success(result); }
    static String read(Path path) { try { return Files.readString(path); } catch (Exception e) { return "Status file unavailable"; } }
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException("Hash unavailable"); }
    }
    static List<Map<String,Object>> persistedRows(JdbcTemplate jdbc, String document, String index) {
        return jdbc.queryForList("SELECT * FROM native_rag_chunk WHERE document_id=? AND index_version=? ORDER BY ordinal_number", document, index)
                .stream().map(row -> {
                    Map<String,Object> identity = new LinkedHashMap<>();
                    for (String key : List.of("id", "document_id", "kb_id", "page_number", "ordinal_number", "index_version", "document_version", "parse_revision", "source_type", "vector_model")) identity.put(key, row.get(key));
                    identity.put("contentSha256", sha(row.get("content").toString().getBytes(StandardCharsets.UTF_8)));
                    identity.put("vectorSha256", sha(row.get("vector_json").toString().getBytes(StandardCharsets.UTF_8)));
                    return identity;
                }).toList();
    }
    static final class SyntheticEmbedding extends RagEmbeddingClient {
        final boolean queryOnly;
        int queryBatches, documentBatches;
        SyntheticEmbedding(boolean queryOnly) {
            super(false, "", "", MODEL, JSON); this.queryOnly = queryOnly;
        }
        @Override public boolean isConfigured() { return true; }
        @Override public boolean isRequested() { return true; }
        @Override public String fingerprint() { return FINGERPRINT; }
        @Override public List<double[]> embed(List<String> input) {
            if (input.equals(List.of(QUERY))) queryBatches++;
            else {
                if (queryOnly) throw new IllegalStateException("Restart probe must never re-embed source documents");
                documentBatches++;
            }
            return input.stream().map(ignored -> new double[]{0.6, 0.8, 0, 0}).toList();
        }
    }
    private static byte[] syntheticPdf() throws Exception {
        try (PDDocument pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (String text : List.of("Gradient descent updates parameters to reduce training loss.", "Momentum carries previous optimization directions across iterations.")) {
                var page = new PDPage(); pdf.addPage(page);
                try (var stream = new PDPageContentStream(pdf, page)) {
                    stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(40, 700); stream.showText(text); stream.endText();
                }
            }
            pdf.save(output); return output.toByteArray();
        }
    }
}
