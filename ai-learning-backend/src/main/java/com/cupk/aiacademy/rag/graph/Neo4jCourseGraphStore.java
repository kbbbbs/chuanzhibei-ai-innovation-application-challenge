package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.util.*;

/** Native entities/relationships retain per-source claims before permission-specific merging. */
@Service
public class Neo4jCourseGraphStore {
    private final Neo4jGraphRepository repository;
    private final ObjectMapper json;

    public Neo4jCourseGraphStore(Neo4jGraphRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public void verify() {
        repository.verify();
    }

    public void save(String graphRef, List<Map<String, Object>> fragments) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>(),
                edges = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> nodeClaims = new LinkedHashMap<>(),
                edgeClaims = new LinkedHashMap<>();
        for (var fragment : fragments) {
            RagSearchHit source = json.convertValue(fragment.get("source"), RagSearchHit.class);
            for (var claim : maps(fragment.get("nodes"))) {
                String id = text(claim.get("id"));
                nodes.computeIfAbsent(
                        id,
                        key ->
                                new LinkedHashMap<>(
                                        Map.of(
                                                "id",
                                                id,
                                                "name",
                                                text(claim.get("label")),
                                                "type",
                                                nodeType(claim))));
                nodeClaims
                        .computeIfAbsent(id, key -> new ArrayList<>())
                        .add(
                                Map.of(
                                        "chunkId",
                                        source.chunkId(),
                                        "label",
                                        text(claim.get("label")),
                                        "type",
                                        nodeType(claim),
                                        "description",
                                        text(claim.get("description")),
                                        "category",
                                        text(claim.get("category"))));
            }
            for (var claim : maps(fragment.get("relations"))) {
                String id = text(claim.get("id"));
                String type = text(claim.get("type"));
                if (!Neo4jGraphRepository.RELATION_TYPES.contains(type)) type = "RELATED_TO";
                String relationType = type;
                edges.computeIfAbsent(
                        id,
                        key ->
                                new LinkedHashMap<>(
                                        Map.of(
                                                "id",
                                                id,
                                                "source",
                                                text(claim.get("source")),
                                                "target",
                                                text(claim.get("target")),
                                                "label",
                                                text(claim.get("label")),
                                                "type",
                                                relationType)));
                edgeClaims
                        .computeIfAbsent(id, key -> new ArrayList<>())
                        .add(
                                Map.of(
                                        "chunkId",
                                        source.chunkId(),
                                        "label",
                                        text(claim.get("label")),
                                        "type",
                                        relationType));
            }
        }
        nodes.forEach((id, node) -> node.put("claimsJson", encode(nodeClaims.get(id))));
        edges.forEach((id, edge) -> edge.put("claimsJson", encode(edgeClaims.get(id))));
        repository.project(
                graphRef, 1, new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()));
    }

    public List<Map<String, Object>> read(String graphRef, List<RagSearchHit> permittedSources) {
        GraphSlice graph = repository.readAll(graphRef, 1);
        Map<String, Map<String, Object>> fragments = new LinkedHashMap<>();
        for (var source : permittedSources) {
            Map<String, Object> fragment = new LinkedHashMap<>();
            fragment.put("source", source);
            fragment.put("nodes", new ArrayList<>());
            fragment.put("relations", new ArrayList<>());
            fragments.put(source.chunkId(), fragment);
        }
        for (var node : graph.nodes())
            for (var claim : claims(node)) {
                var fragment = fragments.get(text(claim.get("chunkId")));
                if (fragment == null) continue;
                append(
                        fragment,
                        "nodes",
                        Map.of(
                                "id",
                                node.get("id"),
                                "label",
                                claim.get("label"),
                                "type",
                                claim.get("type"),
                                "description",
                                text(claim.get("description")),
                                "category",
                                text(claim.get("category"))));
            }
        for (var edge : graph.edges())
            for (var claim : claims(edge)) {
                var fragment = fragments.get(text(claim.get("chunkId")));
                if (fragment == null) continue;
                append(
                        fragment,
                        "relations",
                        Map.of(
                                "id",
                                edge.get("id"),
                                "source",
                                edge.get("source"),
                                "target",
                                edge.get("target"),
                                "type",
                                claim.get("type"),
                                "label",
                                claim.get("label")));
            }
        return List.copyOf(fragments.values());
    }

    public GraphSlice paths(
            String ref, String from, String to, Set<String> nodes, Set<String> edges) {
        return repository.paths(ref, 1, from, to, false, nodes, edges);
    }

    public GraphSlice neighbors(
            String ref,
            String entity,
            int depth,
            String type,
            Set<String> nodes,
            Set<String> edges) {
        return repository.neighbors(ref, 1, entity, depth, type, nodes, edges);
    }

    private String nodeType(Map<String, Object> claim) {
        String type = text(claim.get("type"));
        if (type.isBlank()) type = text(claim.get("category"));
        return Set.of("CONCEPT", "TASK", "ALGORITHM", "MODEL", "METRIC").contains(type)
                ? type
                : "CONCEPT";
    }

    @SuppressWarnings("unchecked")
    private void append(Map<String, Object> fragment, String key, Map<String, Object> claim) {
        ((List<Map<String, Object>>) fragment.get(key)).add(claim);
    }

    private List<Map<String, Object>> claims(Map<String, Object> entity) {
        try {
            return json.readValue(text(entity.get("claimsJson")), new TypeReference<>() {});
        } catch (Exception invalid) {
            throw new RagAccessException("409", "图谱来源记录无法读取，请重新生成图谱");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception invalid) {
            throw new RagAccessException("400", "图谱来源记录无法保存");
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
