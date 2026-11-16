package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagAccessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Neo4jGraphRepositoryTest {
    @Test void disabledStorageFailsExplicitlyWithoutConnecting() {
        var repository = new Neo4jGraphRepository(false, "bolt://127.0.0.1:1", "neo4j", "", "neo4j");
        var error = assertThrows(RagAccessException.class, () -> repository.read("graph", 1, 100));
        assertEquals("503", error.code());
        repository.close();
    }

    @Test void invalidScopeAndRelationshipTypesAreRejectedBeforeDatabaseAccess() {
        var repository = new Neo4jGraphRepository(true, "bolt://127.0.0.1:1", "neo4j", "", "neo4j");
        assertEquals("400", assertThrows(RagAccessException.class,
                () -> repository.read("", 1, 100)).code());
        assertEquals("400", assertThrows(RagAccessException.class,
                () -> repository.neighbors("g", 1, "node", 1, "MATCH (n) DETACH DELETE n")).code());
        assertEquals("400", assertThrows(RagAccessException.class,
                () -> repository.paths("g", 1, "node", "node", false)).code());
        assertEquals("400", assertThrows(RagAccessException.class,
                () -> repository.project("g", 1, List.of(node("n1")),
                        List.of(Map.of("id", "e", "source", "n1", "target", "missing", "type", "HAS_TASK")))).code());
        repository.close();
    }

    static Map<String, Object> node(String id) {
        return Map.of("id", id, "name", id, "type", "CONCEPT", "description", "source-backed explanation",
                "aliases", List.of(), "evidenceIds", List.of("ev"));
    }
}
