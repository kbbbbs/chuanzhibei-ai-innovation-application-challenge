package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Immutable, scope-isolated projections. All result conversion occurs inside the transaction. */
@Repository
public class Neo4jGraphRepository implements AutoCloseable {
    public static final Set<String> RELATION_TYPES =
            Set.of(
                    "HAS_CHAPTER",
                    "COVERS",
                    "IS_A",
                    "HAS_TASK",
                    "HAS_EXAMPLE",
                    "USED_FOR",
                    "MEASURED_BY",
                    "PREREQUISITE_FOR",
                    "RELATED_TO");
    private static final TransactionConfig QUERY_CONFIG =
            TransactionConfig.builder().withTimeout(Duration.ofSeconds(10)).build();
    private final boolean enabled;
    private final String database;
    private final Driver driver;
    private final boolean ownsDriver;

    @Autowired
    public Neo4jGraphRepository(
            Driver driver,
            @Value("${rag.graph.enabled:false}") boolean enabled,
            @Value("${spring.data.neo4j.database:neo4j}") String database) {
        this.driver = driver;
        this.enabled = enabled;
        this.database = database;
        this.ownsDriver = false;
    }

    /** Explicit configuration for isolated integration tests and command-line verification. */
    public Neo4jGraphRepository(
            boolean enabled, String uri, String username, String password, String database) {
        this.enabled = enabled;
        this.database = database;
        this.ownsDriver = true;
        this.driver =
                enabled
                        ? GraphDatabase.driver(
                                uri,
                                AuthTokens.basic(username, password),
                                Config.builder()
                                        .withConnectionTimeout(
                                                5, java.util.concurrent.TimeUnit.SECONDS)
                                        .withConnectionAcquisitionTimeout(
                                                5, java.util.concurrent.TimeUnit.SECONDS)
                                        .withMaxTransactionRetryTime(
                                                5, java.util.concurrent.TimeUnit.SECONDS)
                                        .build())
                        : null;
    }

    public void project(
            String graphId,
            int version,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges) {
        scope(graphId, version);
        validate(nodes, edges);
        String hash = fingerprint(nodes, edges);
        write(
                tx -> {
                    tx.run(
                                    "CREATE CONSTRAINT course_graph_version IF NOT EXISTS FOR"
                                        + " (v:CourseGraphVersion) REQUIRE (v.graphId,v.version) IS"
                                        + " UNIQUE")
                            .consume();
                    tx.run(
                                    "CREATE CONSTRAINT course_graph_entity IF NOT EXISTS FOR"
                                        + " (n:KnowledgeEntity) REQUIRE"
                                        + " (n.graphId,n.graphVersion,n.id) IS UNIQUE")
                            .consume();
                    return null;
                });
        write(
                tx -> {
                    var params =
                            Map.<String, Object>of(
                                    "graphId", graphId, "version", version, "hash", hash);
                    var stored =
                            tx.run(
                                            "MERGE (v:CourseGraphVersion"
                                                + " {graphId:$graphId,version:$version}) ON CREATE"
                                                + " SET v.fingerprint=$hash,v.ready=false SET"
                                                + " v.lock=coalesce(v.lock,0)+1 RETURN"
                                                + " v.fingerprint AS fingerprint,v.ready AS ready",
                                            params)
                                    .single();
                    if (!hash.equals(stored.get("fingerprint").asString()))
                        throw new RagAccessException("409", "该图谱版本已绑定其他内容，请创建新版本");
                    if (stored.get("ready").asBoolean()) return null;
                    var nodeParams = scopeParams(graphId, version);
                    nodeParams.put(
                            "rows", nodes.stream().map(node -> properties(node, true)).toList());
                    tx.run(
                                    "UNWIND $rows AS row MERGE (n:KnowledgeEntity"
                                        + " {graphId:$graphId,graphVersion:$version,id:row.id}) SET"
                                        + " n += row",
                                    nodeParams)
                            .consume();
                    for (String relation : RELATION_TYPES) {
                        var typed =
                                edges.stream()
                                        .filter(edge -> relation.equals(edge.get("type")))
                                        .map(edge -> properties(edge, false))
                                        .toList();
                        if (typed.isEmpty()) continue;
                        var edgeParams = scopeParams(graphId, version);
                        edgeParams.put("rows", typed);
                        tx.run(
                                        "UNWIND $rows AS row MATCH (a:KnowledgeEntity"
                                            + " {graphId:$graphId,graphVersion:$version,id:row.source}),"
                                            + " (b:KnowledgeEntity"
                                            + " {graphId:$graphId,graphVersion:$version,id:row.target})"
                                            + " MERGE (a)-[r:"
                                                + relation
                                                + " {id:row.id}]->(b) SET r +="
                                                + " row,r.graphId=$graphId,r.graphVersion=$version",
                                        edgeParams)
                                .consume();
                    }
                    tx.run(
                                    "MATCH (v:CourseGraphVersion"
                                        + " {graphId:$graphId,version:$version}) SET v.ready=true",
                                    params)
                            .consume();
                    return null;
                });
    }

    public GraphSlice read(String graphId, int version, int limit) {
        scope(graphId, version);
        int bound = Math.max(1, Math.min(100, limit));
        return read(
                tx -> {
                    ready(tx, graphId, version);
                    var params = scopeParams(graphId, version);
                    params.put("limit", bound + 1);
                    var rows =
                            tx.run(
                                            "MATCH (n:KnowledgeEntity"
                                                + " {graphId:$graphId,graphVersion:$version})"
                                                + " RETURN properties(n) AS props ORDER BY n.id"
                                                + " LIMIT $limit",
                                            params)
                                    .list(row -> clean(row.get("props").asMap()));
                    boolean truncated = rows.size() > bound;
                    var visible = rows.subList(0, Math.min(bound, rows.size()));
                    var ids = visible.stream().map(n -> n.get("id")).toList();
                    params.put("ids", ids);
                    var edges =
                            tx.run(
                                            "MATCH (a:KnowledgeEntity)-[r]->(b:KnowledgeEntity)"
                                                + " WHERE a.graphId=$graphId AND"
                                                + " a.graphVersion=$version AND b.graphId=$graphId"
                                                + " AND b.graphVersion=$version AND"
                                                + " r.graphId=$graphId AND r.graphVersion=$version"
                                                + " AND a.id IN $ids AND b.id IN $ids RETURN"
                                                + " properties(r) AS props ORDER BY r.id LIMIT 201",
                                            params)
                                    .list(row -> clean(row.get("props").asMap()));
                    return new GraphSlice(
                            List.copyOf(visible),
                            List.copyOf(edges.subList(0, Math.min(200, edges.size()))),
                            truncated || edges.size() > 200);
                });
    }

    public void verify() {
        read(
                tx -> {
                    tx.run("RETURN 1").consume();
                    return null;
                });
    }

    /** Complete storage reads keep extraction coverage independent of viewport limits. */
    public GraphSlice readAll(String graphId, int version) {
        scope(graphId, version);
        return read(
                tx -> {
                    ready(tx, graphId, version);
                    var params = scopeParams(graphId, version);
                    var nodes =
                            tx.run(
                                            "MATCH (n:KnowledgeEntity"
                                                + " {graphId:$graphId,graphVersion:$version})"
                                                + " RETURN properties(n) AS props ORDER BY n.id",
                                            params)
                                    .list(row -> clean(row.get("props").asMap()));
                    var edges =
                            tx.run(
                                            "MATCH (a:KnowledgeEntity"
                                                + " {graphId:$graphId,graphVersion:$version})-[r]->(b:KnowledgeEntity"
                                                + " {graphId:$graphId,graphVersion:$version}) WHERE"
                                                + " r.graphId=$graphId AND r.graphVersion=$version"
                                                + " RETURN properties(r) AS props ORDER BY r.id",
                                            params)
                                    .list(row -> clean(row.get("props").asMap()));
                    return new GraphSlice(nodes, edges, false);
                });
    }

    public GraphSlice paths(
            String graphId,
            int version,
            String from,
            String to,
            boolean learningOnly,
            Set<String> allowedNodes,
            Set<String> allowedEdges) {
        scope(graphId, version);
        identifier(from);
        identifier(to);
        if (from.equals(to)) throw invalid("请选择两个不同的知识点");
        return pathsQuery(
                graphId,
                version,
                "MATCH p=shortestPath((a:KnowledgeEntity"
                    + " {graphId:$graphId,graphVersion:$version,id:$from})-["
                        + (learningOnly ? ":PREREQUISITE_FOR" : "")
                        + "*1..8]"
                        + (learningOnly ? "->" : "-")
                        + "(b:KnowledgeEntity {graphId:$graphId,graphVersion:$version,id:$to})) ",
                Map.of(
                        "from",
                        from,
                        "to",
                        to,
                        "relationType",
                        "",
                        "allowedNodes",
                        List.copyOf(allowedNodes),
                        "allowedEdges",
                        List.copyOf(allowedEdges)));
    }

    public GraphSlice neighbors(
            String graphId,
            int version,
            String entityId,
            int depth,
            String relationType,
            Set<String> allowedNodes,
            Set<String> allowedEdges) {
        scope(graphId, version);
        identifier(entityId);
        if (depth < 1 || depth > 2) throw invalid("展开深度应为 1 或 2");
        if (relationType != null
                && !relationType.isBlank()
                && !RELATION_TYPES.contains(relationType)) throw invalid("不支持的关系类型");
        return pathsQuery(
                graphId,
                version,
                "MATCH p=(a:KnowledgeEntity"
                    + " {graphId:$graphId,graphVersion:$version,id:$entityId})-[*1.."
                        + depth
                        + "]-(b:KnowledgeEntity) ",
                Map.of(
                        "entityId",
                        entityId,
                        "relationType",
                        relationType == null ? "" : relationType,
                        "allowedNodes",
                        List.copyOf(allowedNodes),
                        "allowedEdges",
                        List.copyOf(allowedEdges)));
    }

    public GraphSlice neighbors(
            String graphId, int version, String entityId, int depth, String relationType) {
        scope(graphId, version);
        identifier(entityId);
        if (depth < 1 || depth > 2) throw invalid("展开深度应为 1 或 2");
        if (relationType != null
                && !relationType.isBlank()
                && !RELATION_TYPES.contains(relationType)) throw invalid("不支持的关系类型");
        return pathsQuery(
                graphId,
                version,
                "MATCH p=(a:KnowledgeEntity {graphId:$graphId,graphVersion:$version,id:$entityId})"
                        + "-[*1.."
                        + depth
                        + "]-(b:KnowledgeEntity) ",
                Map.of(
                        "entityId",
                        entityId,
                        "relationType",
                        relationType == null ? "" : relationType));
    }

    public GraphSlice paths(
            String graphId, int version, String from, String to, boolean learningOnly) {
        scope(graphId, version);
        identifier(from);
        identifier(to);
        if (from.equals(to)) throw invalid("请选择两个不同的知识点");
        String relationship = learningOnly ? "PREREQUISITE_FOR" : "";
        String direction = learningOnly ? "->" : "-";
        return pathsQuery(
                graphId,
                version,
                "MATCH p=shortestPath((a:KnowledgeEntity"
                    + " {graphId:$graphId,graphVersion:$version,id:$from})-["
                        + (relationship.isEmpty() ? "" : ":" + relationship)
                        + "*1..8]"
                        + direction
                        + "(b:KnowledgeEntity {graphId:$graphId,graphVersion:$version,id:$to})) ",
                Map.of("from", from, "to", to, "relationType", ""));
    }

    private GraphSlice pathsQuery(
            String graphId, int version, String match, Map<String, Object> extra) {
        return read(
                tx -> {
                    ready(tx, graphId, version);
                    var params = scopeParams(graphId, version);
                    params.putAll(extra);
                    var rows =
                            tx.run(
                                            match
                                                    + "WHERE all(n IN nodes(p) WHERE"
                                                    + " n.graphId=$graphId AND"
                                                    + " n.graphVersion=$version) AND all(r IN"
                                                    + " relationships(p) WHERE r.graphId=$graphId"
                                                    + " AND r.graphVersion=$version AND"
                                                    + " ($relationType='' OR"
                                                    + " type(r)=$relationType)) "
                                                    + (extra.containsKey("allowedEdges")
                                                            ? "AND all(n IN nodes(p) WHERE n.id IN"
                                                                  + " $allowedNodes) AND all(r IN"
                                                                  + " relationships(p) WHERE r.id"
                                                                  + " IN $allowedEdges) "
                                                            : "")
                                                    + "RETURN [n IN nodes(p) | properties(n)] AS"
                                                    + " nodes,[r IN relationships(p) |"
                                                    + " properties(r)] AS edges LIMIT 201",
                                            params)
                                    .list(
                                            row ->
                                                    Map.of(
                                                            "nodes",
                                                            row.get("nodes")
                                                                    .asList(v -> clean(v.asMap())),
                                                            "edges",
                                                            row.get("edges")
                                                                    .asList(
                                                                            v ->
                                                                                    clean(
                                                                                            v
                                                                                                    .asMap()))));
                    var nodes = new LinkedHashMap<String, Map<String, Object>>();
                    var edges = new LinkedHashMap<String, Map<String, Object>>();
                    boolean truncated = rows.size() > 200;
                    for (var row : rows.subList(0, Math.min(200, rows.size()))) {
                        for (var node : row.get("nodes")) {
                            String id = node.get("id").toString();
                            if (nodes.size() < 100 || nodes.containsKey(id)) nodes.put(id, node);
                            else truncated = true;
                        }
                        for (var edge : row.get("edges")) {
                            String id = edge.get("id").toString();
                            if (edges.size() < 200 || edges.containsKey(id)) edges.put(id, edge);
                            else truncated = true;
                        }
                    }
                    edges.values()
                            .removeIf(
                                    edge ->
                                            !nodes.containsKey(edge.get("source").toString())
                                                    || !nodes.containsKey(
                                                            edge.get("target").toString()));
                    return new GraphSlice(
                            List.copyOf(nodes.values()), List.copyOf(edges.values()), truncated);
                });
    }

    private void ready(TransactionContext tx, String graphId, int version) {
        if (!tx.run(
                        "MATCH (v:CourseGraphVersion"
                            + " {graphId:$graphId,version:$version,ready:true}) RETURN v.version",
                        scopeParams(graphId, version))
                .hasNext()) throw new RagAccessException("503", "图谱尚未完成同步，请稍后重试");
    }

    private <T> T read(Function<TransactionContext, T> work) {
        return execute(work, false);
    }

    private <T> T write(Function<TransactionContext, T> work) {
        return execute(work, true);
    }

    private <T> T execute(Function<TransactionContext, T> work, boolean write) {
        if (!enabled) throw new RagAccessException("503", "Neo4j 图数据库尚未启用");
        try (var session = driver.session(SessionConfig.builder().withDatabase(database).build())) {
            return write
                    ? session.executeWrite(work::apply, QUERY_CONFIG)
                    : session.executeRead(work::apply, QUERY_CONFIG);
        } catch (Neo4jException | IllegalStateException failure) {
            throw new RagAccessException("503", "Neo4j 图数据库暂时不可用，请稍后重试");
        }
    }

    private static void validate(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        if (nodes == null || edges == null) throw invalid("图谱实体和关系不能为空");
        var ids = new HashSet<String>();
        for (var node : nodes) {
            if (node == null) throw invalid("实体格式无效");
            String id = text(node, "id");
            identifier(id);
            if (!ids.add(id)) throw invalid("实体 ID 重复");
            text(node, "name");
            text(node, "type");
        }
        var edgeIds = new HashSet<String>();
        for (var edge : edges) {
            if (edge == null) throw invalid("关系格式无效");
            String id = text(edge, "id");
            identifier(id);
            if (!edgeIds.add(id)
                    || !ids.contains(text(edge, "source"))
                    || !ids.contains(text(edge, "target"))) throw invalid("关系 ID 重复或端点不存在");
            if (!RELATION_TYPES.contains(text(edge, "type"))) throw invalid("不支持的关系类型");
        }
    }

    private static Map<String, Object> properties(Map<String, Object> input, boolean node) {
        var result = new LinkedHashMap<String, Object>();
        var keys =
                node
                        ? List.of(
                                "id",
                                "name",
                                "type",
                                "description",
                                "aliases",
                                "evidenceIds",
                                "claimsJson")
                        : List.of(
                                "id",
                                "source",
                                "target",
                                "type",
                                "label",
                                "explanation",
                                "origin",
                                "evidenceIds",
                                "reviewStatus",
                                "reviewedBy",
                                "reviewedAt",
                                "claimsJson");
        for (String key : keys) if (input.get(key) != null) result.put(key, input.get(key));
        return result;
    }

    private static Map<String, Object> clean(Map<String, Object> input) {
        var result = new LinkedHashMap<>(input);
        result.remove("graphId");
        result.remove("graphVersion");
        return result;
    }

    private static Map<String, Object> scopeParams(String graphId, int version) {
        var result = new LinkedHashMap<String, Object>();
        result.put("graphId", graphId);
        result.put("version", version);
        return result;
    }

    private static Map<String, Object> parameters(
            String graphId, int version, Object id, Map<String, Object> props) {
        var result = scopeParams(graphId, version);
        result.put("id", id);
        result.put("props", props);
        return result;
    }

    private static String fingerprint(
            List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        try {
            var json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            var sortedNodes =
                    nodes.stream()
                            .map(n -> properties(n, true))
                            .sorted(Comparator.comparing(n -> n.get("id").toString()))
                            .toList();
            var sortedEdges =
                    edges.stream()
                            .map(e -> properties(e, false))
                            .sorted(Comparator.comparing(e -> e.get("id").toString()))
                            .toList();
            byte[] bytes =
                    json.writeValueAsString(Map.of("nodes", sortedNodes, "edges", sortedEdges))
                            .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception failure) {
            throw invalid("图谱内容无法序列化");
        }
    }

    private static void scope(String graphId, int version) {
        identifier(graphId);
        if (version <= 0) throw invalid("图谱版本无效");
    }

    private static void identifier(String id) {
        if (id == null || id.isBlank() || id.length() > 100) throw invalid("图谱标识无效");
    }

    private static String text(Map<String, Object> value, String key) {
        if (!(value.get(key) instanceof String text) || text.isBlank())
            throw invalid("图谱缺少有效的 " + key);
        return text;
    }

    private static RagAccessException invalid(String message) {
        return new RagAccessException("400", message);
    }

    @Override
    public void close() {
        if (ownsDriver && driver != null) driver.close();
    }
}
