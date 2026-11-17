package com.cupk.aiacademy.rag.graph;

import static org.junit.jupiter.api.Assertions.*;

import com.cupk.aiacademy.rag.RagSearchHit;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

@EnabledIfEnvironmentVariable(named = "NEO4J_INTEGRATION_TEST", matches = "true")
class CourseNeo4jStoreIntegrationTest {
    Neo4jGraphRepository repository;
    Neo4jCourseGraphStore store;
    String graph = "course-restore-test-" + UUID.randomUUID();

    @BeforeEach
    void open() {
        repository =
                new Neo4jGraphRepository(
                        true,
                        System.getenv("NEO4J_TEST_URI"),
                        "neo4j",
                        System.getenv("NEO4J_TEST_PASSWORD"),
                        "neo4j");
        store = new Neo4jCourseGraphStore(repository, new ObjectMapper());
    }

    @AfterEach
    void close() {
        repository.close();
    }

    @Test
    void actualNeo4jEntitiesAndRelationshipsRetainAtomicSourceClaimsAcrossConnections() {
        var privateSource = source("private");
        var publicSource = source("public");
        store.save(
                graph,
                List.of(
                        fragment(privateSource, "private description", "Alpha", "TASK"),
                        fragment(publicSource, "public description", "ALPHA", "MODEL")));
        var nativeGraph = repository.readAll(graph, 1);
        assertEquals(2, nativeGraph.nodes().size());
        assertEquals(1, nativeGraph.edges().size());
        repository.close();
        open();
        var visible = store.read(graph, List.of(publicSource));
        String text = visible.toString();
        assertTrue(text.contains("public description"));
        assertFalse(text.contains("private description"));
        assertEquals(1, visible.size());
        assertEquals(publicSource, visible.getFirst().get("source"));
        var node =
                ((List<Map<String, Object>>) visible.getFirst().get("nodes"))
                        .stream()
                                .filter(item -> "a".equals(item.get("id")))
                                .findFirst()
                                .orElseThrow();
        assertEquals("ALPHA", node.get("label"));
        assertEquals("MODEL", node.get("type"));
    }

    @Test
    void storageIncludesMoreThanOneThousandEntitiesAndReadsTheCompleteGraph() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int index = 0; index < 1005; index++)
            nodes.add(
                    Map.of("id", "n" + index, "label", "Concept" + index, "description", "source"));
        var fragment =
                Map.<String, Object>of(
                        "source", source("large"), "nodes", nodes, "relations", List.of());
        store.save(graph, List.of(fragment));
        assertEquals(1005, repository.readAll(graph, 1).nodes().size());
    }

    @Test
    void emptyConceptExtractionIsAValidStoredVersion() {
        var source = source("empty");
        store.save(
                graph,
                List.of(Map.of("source", source, "nodes", List.of(), "relations", List.of())));
        assertTrue(repository.readAll(graph, 1).nodes().isEmpty());
        assertEquals(1, store.read(graph, List.of(source)).size());
    }

    @Test
    void nativePathsExcludeUnauthorizedEdgesAndRetainAnAllowedAlternative() {
        repository.project(
                graph,
                1,
                List.of(nativeNode("a"), nativeNode("b"), nativeNode("c")),
                List.of(
                        nativeEdge("private", "a", "c"),
                        nativeEdge("ab", "a", "b"),
                        nativeEdge("bc", "b", "c")));
        var path =
                repository.paths(
                        graph, 1, "a", "c", false, Set.of("a", "b", "c"), Set.of("ab", "bc"));
        assertEquals(
                Set.of("ab", "bc"),
                new HashSet<>(
                        path.edges().stream().map(edge -> edge.get("id").toString()).toList()));
        assertEquals(3, path.nodes().size());
    }

    private Map<String, Object> fragment(RagSearchHit source, String description) {
        return fragment(source, description, "Alpha", "CONCEPT");
    }

    private Map<String, Object> fragment(
            RagSearchHit source, String description, String label, String category) {
        return Map.of(
                "source",
                source,
                "nodes",
                List.of(
                        Map.of(
                                "id",
                                "a",
                                "label",
                                label,
                                "description",
                                description,
                                "category",
                                category),
                        Map.of("id", "b", "label", "Beta")),
                "relations",
                List.of(Map.of("id", "ab", "source", "a", "target", "b", "label", "supports")));
    }

    private RagSearchHit source(String id) {
        return new RagSearchHit(
                id,
                id,
                "base",
                id,
                "Alpha supports Beta",
                1,
                1,
                1,
                "[1]",
                List.of(),
                id + "-index",
                1,
                1);
    }

    private Map<String, Object> nativeNode(String id) {
        return Map.of("id", id, "name", id, "type", "CONCEPT");
    }

    private Map<String, Object> nativeEdge(String id, String from, String to) {
        return Map.of(
                "id", id, "source", from, "target", to, "type", "RELATED_TO", "label", "related");
    }
}
