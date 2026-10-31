package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.UserContext;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cupk.aiacademy.rag.K03PersistenceRestartTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Standalone test-scope reader. Input is a private temporary JSON file, never credentials on the command line. */
public final class K03PersistenceRestartProbe {
    private K03PersistenceRestartProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("One private restart-config file is required");
        JsonNode config = JSON.readTree(Path.of(args[0]).toFile());
        assertNotEquals(config.path("parentPid").asLong(), ProcessHandle.current().pid());
        // IFEXISTS disallows accidentally proving a fresh empty database instead of reopening the saved file.
        var jdbc = jdbc(config.path("jdbcUrl").asText() + ";IFEXISTS=TRUE");
        String document = config.path("document").asText();
        String activeIndex = config.path("activeIndex").asText();
        String kb = config.path("kb").asText();
        var embedding = new SyntheticEmbedding(true);
        try {
            var before = persistedRows(jdbc, document, activeIndex);
            assertEquals(config.path("rows"), JSON.valueToTree(before), "Disk rows must match the parent snapshot before initialize");
            assertEquals(config.path("rowCount").asInt(), jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk", Integer.class));
            assertEquals(config.path("indexCount").asInt(), jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version", Integer.class));
            var service = new NativeRagService(jdbc, new RagDocumentParser(), embedding, JSON, config.path("originals").asText());
            service.initialize();
            login(config.path("owner").asInt());
            Map<String,Object> persistedDocument = data(service.getDocument(document));
            assertEquals(activeIndex, persistedDocument.get("active_index_version"));
            assertEquals(config.path("documentVersion").asInt(), ((Number) persistedDocument.get("document_version")).intValue());
            assertEquals(config.path("parseRevision").asInt(), ((Number) persistedDocument.get("parse_revision")).intValue());
            String sourceHash = sha(Files.readAllBytes(service.originalFile(document).path()));
            assertEquals(config.path("sourceSha256").asText(), sourceHash);

            var rows = jdbc.queryForList("SELECT * FROM native_rag_chunk WHERE document_id=? AND index_version=? ORDER BY ordinal_number", document, activeIndex);
            for (double score : RagBm25.score(QUERY, rows.stream().map(row -> row.get("content").toString()).toList()))
                assertEquals(0.0, score, "A keyword-only fallback must not satisfy this persistence proof");
            for (var row : rows) {
                double[] vector = JSON.readValue(row.get("vector_json").toString(), double[].class);
                assertArrayEquals(new double[]{0.6, 0.8, 0, 0}, vector, 1e-15);
                assertEquals(FINGERPRINT, row.get("vector_model"));
            }
            var storedIndex = jdbc.queryForMap("SELECT * FROM native_rag_index_version WHERE id=?", activeIndex);
            assertEquals("HYBRID_READY", storedIndex.get("state"));
            assertEquals(MODEL, storedIndex.get("embedding_model"));
            assertEquals(FINGERPRINT, storedIndex.get("embedding_fingerprint"));
            assertEquals(DIMENSIONS, ((Number) storedIndex.get("dimensions")).intValue());
            assertEquals(true, storedIndex.get("normalized"));
            assertEquals(config.path("parserRevision").asText(), storedIndex.get("parser_revision"));
            assertEquals(config.path("chunkRevision").asText(), storedIndex.get("chunk_revision"));

            var response = (RagSearchResponse) success(service.search(new RagSearchRequest(List.of(kb), QUERY, 5)));
            assertEquals("HYBRID", response.actualMode());
            assertEquals("hybrid", response.mode());
            assertNull(response.degradedReason());
            assertEquals(rows.size(), response.hits().size());
            assertEquals(1, response.indexVersions().size());
            RagIndexSnapshot snapshot = response.indexVersions().getFirst();
            assertEquals(activeIndex, snapshot.indexVersion());
            assertNotEquals(config.path("oldIndex").asText(), snapshot.indexVersion());
            assertEquals(document, snapshot.documentId());
            assertEquals(config.path("documentVersion").asInt(), snapshot.documentVersion());
            assertEquals(config.path("parseRevision").asInt(), snapshot.parseRevision());
            assertEquals(config.path("parserRevision").asText(), snapshot.parserRevision());
            assertEquals(config.path("chunkRevision").asText(), snapshot.chunkRevision());
            assertEquals(MODEL, snapshot.embeddingModel());
            assertEquals(FINGERPRINT, snapshot.embeddingFingerprint());
            assertEquals(DIMENSIONS, snapshot.dimensions()); assertTrue(snapshot.normalized());
            for (var hit : response.hits()) {
                var originalRow = rows.stream().filter(row -> row.get("id").equals(hit.chunkId())).findFirst().orElseThrow();
                assertEquals(document, hit.documentId()); assertEquals(kb, hit.kbId());
                assertEquals(activeIndex, hit.indexVersion());
                assertEquals(config.path("documentVersion").asInt(), hit.documentVersion());
                assertEquals(config.path("parseRevision").asInt(), hit.parseRevision());
                assertEquals(((Number) originalRow.get("page_number")).intValue(), hit.page());
                assertEquals(((Number) originalRow.get("ordinal_number")).intValue(), hit.ordinal());
                assertEquals(originalRow.get("content"), hit.content());
                assertEquals("synthetic-restart.pdf", hit.title());
            }
            assertEquals("403", service.search(new RagSearchRequest(List.of(config.path("forbiddenKb").asText()), QUERY, 5)).getCode());
            assertEquals(1, embedding.queryBatches); assertEquals(0, embedding.documentBatches);
            assertEquals(config.path("rows"), JSON.valueToTree(persistedRows(jdbc, document, activeIndex)));
            assertEquals(config.path("rowCount").asInt(), jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk", Integer.class));
            assertEquals(config.path("indexCount").asInt(), jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version", Integer.class));

            Map<String,Object> report = new LinkedHashMap<>();
            report.put("status", "PASS"); report.put("evidenceType", "SYNTHETIC_PERSISTENCE_ONLY");
            report.put("limitation", "Synthetic normalized vectors verify H2 file/second JVM persistence and authorization, not provider relevance or a MySQL server restart.");
            report.put("childPid", ProcessHandle.current().pid()); report.put("parentPid", config.path("parentPid").asLong());
            report.put("actualMode", response.actualMode()); report.put("degradedReason", response.degradedReason());
            report.put("sourceSha256", sourceHash); report.put("persistedActiveVectors", rows.size());
            report.put("queryEmbeddingBatches", embedding.queryBatches); report.put("documentEmbeddingBatches", embedding.documentBatches);
            report.put("lexicalMatches", 0); report.put("authorizedScopeVerified", true); report.put("diskRowsUnchanged", true);
            report.put("snapshot", snapshot); report.put("rowMetadata", before);
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(config.path("result").asText()).toFile(), report);
            // No source text, provider URL, credentials, private paths or vector payloads are printed.
            System.out.println("K03_RESTART_PASS vectors=" + rows.size() + " dimensions=" + DIMENSIONS
                    + " sourceSha256=" + sourceHash + " indexVersion=" + activeIndex);
        } finally {
            UserContext.clear();
            jdbc.execute("SHUTDOWN");
        }
    }
}
