package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagAccessException;

import java.util.*;

/** Validates candidate facts against the selected immutable source chunk set. */
final class GraphSnapshotValidator {
    static final Set<String> NODE_TYPES = Set.of("CONCEPT", "ALGORITHM", "TASK", "MODEL", "METRIC");
    static final Set<String> RELATION_TYPES = Set.of("HAS_TASK", "HAS_EXAMPLE", "IS_A", "USED_FOR", "MEASURED_BY", "PREREQUISITE_FOR", "RELATED_TO", "COVERS");
    private static final Map<String, String> LABELS = Map.of(
            "HAS_TASK", "任务类型", "HAS_EXAMPLE", "方法示例", "IS_A", "属于", "USED_FOR", "用于",
            "MEASURED_BY", "评价依据", "PREREQUISITE_FOR", "建议先学", "RELATED_TO", "相关", "COVERS", "包含");

    static Map<String, Object> validate(List<Map<String, Object>> inputNodes, List<Map<String, Object>> inputEdges, Set<String> chunkIds) {
        if (inputNodes == null || inputEdges == null || inputNodes.size() > 500 || inputEdges.size() > 1000)
            throw invalid("每章最多 500 个实体和 1000 条关系");
        Set<String> ids = new HashSet<>();
        Map<String, String> types = new HashMap<>();
        Set<String> names = new HashSet<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> item : inputNodes) {
            if (item == null) throw invalid("实体不能为空");
            String id = id(item.get("id"));
            if (!ids.add(id)) throw invalid("实体和关系 ID 必须唯一");
            String type = text(item.get("type"), 30);
            if (!NODE_TYPES.contains(type)) throw invalid("未知实体类型");
            String name = text(item.get("name"), 100);
            if (!names.add(type + ":" + name.strip().toLowerCase(Locale.ROOT))) throw invalid("同类型实体名称重复，请合并或说明不同含义");
            types.put(id, type);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id); node.put("name", name); node.put("type", type);
            node.put("description", text(item.get("description"), 2000));
            node.put("aliases", strings(item.get("aliases"), 20, 100));
            node.put("evidenceIds", evidence(item.get("evidenceIds"), chunkIds, false));
            nodes.add(node);
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> facts = new HashSet<>();
        Map<String, List<String>> prerequisites = new HashMap<>();
        for (Map<String, Object> item : inputEdges) {
            if (item == null) throw invalid("关系不能为空");
            String id = id(item.get("id"));
            if (!ids.add(id)) throw invalid("实体和关系 ID 必须唯一");
            String source = id(item.get("source")), target = id(item.get("target"));
            if (!types.containsKey(source) || !types.containsKey(target) || source.equals(target)) throw invalid("关系端点不存在或与自身连接");
            String type = text(item.get("type"), 30);
            if (!RELATION_TYPES.contains(type)) throw invalid("未知关系类型");
            if (!facts.add(source + ":" + type + ":" + target)) throw invalid("同一知识事实重复");
            validateEndpoints(type, types.get(source), types.get(target));
            String origin = text(item.getOrDefault("origin", "DOCUMENT"), 20);
            if (!Set.of("DOCUMENT", "TEACHER").contains(origin)) throw invalid("未知关系来源");
            if ("PREREQUISITE_FOR".equals(type)) {
                if (!"TEACHER".equals(origin)) throw invalid("学习先修关系需要教师确认");
                prerequisites.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", id); edge.put("source", source); edge.put("target", target); edge.put("type", type);
            edge.put("label", item.containsKey("label") ? text(item.get("label"), 40) : LABELS.get(type));
            edge.put("explanation", text(item.get("explanation"), 2000));
            edge.put("origin", origin); edge.put("reviewStatus", "PENDING");
            edge.put("evidenceIds", evidence(item.get("evidenceIds"), chunkIds, "TEACHER".equals(origin)));
            edges.add(edge);
        }
        Set<String> done = new HashSet<>();
        for (String node : prerequisites.keySet()) detectCycle(node, prerequisites, new HashSet<>(), done);
        return Map.of("nodes", List.copyOf(nodes), "edges", List.copyOf(edges));
    }

    private static void validateEndpoints(String relation, String source, String target) {
        boolean valid = switch (relation) {
            case "HAS_TASK" -> "TASK".equals(target) && Set.of("CONCEPT", "ALGORITHM", "MODEL").contains(source);
            case "USED_FOR" -> Set.of("ALGORITHM", "MODEL", "CONCEPT", "METRIC").contains(source) && Set.of("TASK", "CONCEPT").contains(target);
            case "MEASURED_BY" -> Set.of("METRIC", "CONCEPT").contains(target);
            case "IS_A" -> source.equals(target) || "CONCEPT".equals(target);
            default -> true;
        };
        if (!valid) throw invalid("关系两端的实体类型不符合定义");
    }

    private static void detectCycle(String node, Map<String, List<String>> links, Set<String> active, Set<String> done) {
        if (done.contains(node)) return;
        if (!active.add(node)) throw invalid("学习先修关系包含循环");
        for (String next : links.getOrDefault(node, List.of())) detectCycle(next, links, active, done);
        active.remove(node); done.add(node);
    }

    private static List<String> evidence(Object value, Set<String> chunkIds, boolean allowEmpty) {
        List<String> ids = strings(value, 30, 100);
        if ((!allowEmpty && ids.isEmpty()) || !chunkIds.containsAll(ids)) throw invalid("知识事实必须引用本章当前版本的真实切片");
        return ids;
    }

    static List<String> strings(Object value, int maxItems, int maxLength) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values) || values.size() > maxItems) throw invalid("列表格式或长度无效");
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = text(item, maxLength);
            if (!result.contains(text)) result.add(text);
        }
        return List.copyOf(result);
    }

    static String id(Object value) {
        String id = text(value, 100);
        if (!id.matches("[\\p{L}\\p{N}_-]+")) throw invalid("ID 只能包含字母、数字、下划线或连字符");
        return id;
    }

    static String text(Object value, int max) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > max) throw invalid("必填内容为空或超出长度限制");
        return text.strip();
    }

    static RagAccessException invalid(String message) { return new RagAccessException("400", message); }
}
