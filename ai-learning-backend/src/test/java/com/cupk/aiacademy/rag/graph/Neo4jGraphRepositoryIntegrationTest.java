package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in tests against a running, authenticated Neo4j. Each run owns new graph IDs. */
@EnabledIfEnvironmentVariable(named = "NEO4J_INTEGRATION_TEST", matches = "true")
class Neo4jGraphRepositoryIntegrationTest {
    private Neo4jGraphRepository repository;
    private final String graph = "storage-test-" + UUID.randomUUID();

    @BeforeEach void open() { repository = connect(); }
    @AfterEach void close() { repository.close(); }

    @Test void persistsAcrossConnectionsAndIdempotentProjectionCannotMutateVersion() {
        var nodes = List.of(node("a"), node("b"));
        Map<String, Object> signed = new java.util.LinkedHashMap<>(edge("ab", "a", "b", "HAS_TASK"));
        signed.put("reviewedBy", 7);
        signed.put("reviewedAt", "2026-11-15T08:00:00Z");
        var edges = List.of(signed);
        repository.project(graph, 1, nodes, edges);
        repository.project(graph, 1, nodes, edges);
        repository.close();
        repository = connect();
        assertEquals(2, repository.read(graph, 1, 100).nodes().size());
        assertEquals(1, repository.read(graph, 1, 100).edges().size());
        var persisted = repository.read(graph, 1, 100).edges().getFirst();
        assertNotNull(persisted.get("reviewedBy"));
        assertEquals(7, ((Number) persisted.get("reviewedBy")).intValue());
        assertEquals("2026-11-15T08:00:00Z", persisted.get("reviewedAt"));
        assertEquals("409", assertThrows(RagAccessException.class,
                () -> repository.project(graph, 1, nodes, List.of())).code());
        assertEquals(1, repository.read(graph, 1, 100).edges().size());
        repository.project(graph, 2, nodes, List.of());
        assertTrue(repository.read(graph, 2, 100).edges().isEmpty());
        assertEquals(1, repository.read(graph, 1, 100).edges().size());
    }

    @Test void neighborhoodAndPathsStayInsideGraphAndDistinguishLearningDirection() {
        var nodes = List.of(node("a"), node("b"), node("c"));
        var edges = List.of(edge("ab", "a", "b", "PREREQUISITE_FOR"), edge("bc", "b", "c", "HAS_TASK"));
        repository.project(graph, 1, nodes, edges);
        repository.project(graph + "-other", 1, List.of(node("a"), node("x")), List.of(edge("ax", "a", "x", "HAS_TASK")));
        var first = repository.neighbors(graph, 1, "a", 1, "");
        assertEquals(2, first.nodes().size());
        assertEquals(1, first.edges().size());
        assertTrue(first.nodes().stream().noneMatch(n -> n.get("id").equals("x")));
        assertEquals(3, repository.neighbors(graph, 1, "a", 2, "").nodes().size());
        assertTrue(repository.neighbors(graph, 1, "a", 1, "HAS_TASK").edges().isEmpty());
        assertEquals(2, repository.paths(graph, 1, "a", "c", false).edges().size());
        assertTrue(repository.paths(graph, 1, "a", "c", true).edges().isEmpty());
        assertEquals(1, repository.paths(graph, 1, "a", "b", true).edges().size());
        assertTrue(repository.paths(graph, 1, "b", "a", true).edges().isEmpty());
    }

    @Test void limitsReturnExplicitTruncationAndNeverDanglingEdges() {
        var nodes = new ArrayList<Map<String, Object>>();
        var edges = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 105; i++) nodes.add(node(String.format("n%03d", i)));
        for (int i = 1; i < 105; i++) edges.add(edge("e" + i, "n000", String.format("n%03d", i), "RELATED_TO"));
        repository.project(graph, 1, nodes, edges);
        var slice = repository.read(graph, 1, 100);
        assertTrue(slice.truncated());
        assertEquals(100, slice.nodes().size());
        var ids = slice.nodes().stream().map(n -> n.get("id")).toList();
        assertTrue(slice.edges().stream().allMatch(e -> ids.contains(e.get("source")) && ids.contains(e.get("target"))));
        var neighborhood = repository.neighbors(graph, 1, "n000", 1, "");
        assertTrue(neighborhood.truncated());
        assertTrue(neighborhood.nodes().size() <= 100);
    }

    private static Neo4jGraphRepository connect() {
        return new Neo4jGraphRepository(true, System.getenv().getOrDefault("NEO4J_URI", "bolt://127.0.0.1:7687"),
                System.getenv().getOrDefault("NEO4J_USERNAME", "neo4j"), System.getenv("NEO4J_PASSWORD"), "neo4j");
    }

    private static Map<String, Object> node(String id) { return Neo4jGraphRepositoryTest.node(id); }
    private static Map<String, Object> edge(String id, String from, String to, String type) {
        return Map.of("id", id, "source", from, "target", to, "type", type, "label", type,
                "explanation", "verified relation", "origin", "DOCUMENT", "reviewStatus", "APPROVED", "evidenceIds", List.of("ev"));
    }
}
