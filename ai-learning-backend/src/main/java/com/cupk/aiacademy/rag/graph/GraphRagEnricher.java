package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.cupk.aiacademy.rag.RagSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** One-hop graph expansion over an already authorized, current-index candidate set. */
public final class GraphRagEnricher {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_PATHS = 3;

  private GraphRagEnricher() {}

  public static RagSearchResponse enrich(
      int courseId,
      String query,
      Map<String, Object> graph,
      RagSearchResponse baseline,
      List<RagSearchHit> authorized,
      int requestedLimit) {
    if (graph == null) return baseline;
    if (!Objects.equals(String.valueOf(courseId), String.valueOf(graph.get("courseId"))))
      throw new RagAccessException("403", "图谱不属于当前课程");
    Map<String, RagSearchHit> allowed = new LinkedHashMap<>();
    authorized.forEach(hit -> allowed.put(hit.chunkId(), hit));
    Map<String, Concept> concepts = new LinkedHashMap<>();
    String normalized = normalizeWords(query);
    Set<String> matched = new LinkedHashSet<>();
    for (Map<String, Object> node : maps(graph.get("nodes"))) {
      String id = text(node.get("id"));
      String label = text(node.get("label"));
      List<RagSearchHit> citations = GraphRetrievalPath.remap(hits(node.get("citations")), allowed);
      if (id.isBlank() || label.isBlank() || citations.isEmpty()) continue;
      concepts.put(id, new Concept(id, label, citations));
      if (matches(normalized, label) || aliasesMatch(normalized, node.get("aliases")))
        matched.add(id);
    }
    matched =
        matched.stream()
            .sorted(
                Comparator.comparingInt((String id) -> normalize(concepts.get(id).label()).length())
                    .reversed())
            .limit(3)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    if (matched.isEmpty()) return baseline;
    List<GraphRetrievalPath> paths = new ArrayList<>();
    Map<String, RagSearchHit> expansion = new LinkedHashMap<>();
    for (Map<String, Object> edge : maps(graph.get("relations"))) {
      String source = text(edge.get("source"));
      String target = text(edge.get("target"));
      if (!matched.contains(source) && !matched.contains(target)) continue;
      Concept from = concepts.get(source);
      Concept to = concepts.get(target);
      List<RagSearchHit> relationSources =
          GraphRetrievalPath.remap(hits(edge.get("citations")), allowed);
      if (from == null || to == null || source.equals(target) || relationSources.isEmpty())
        continue;
      Concept origin = matched.contains(source) ? from : to;
      Concept neighbor = matched.contains(source) ? to : from;
      Set<String> originChunks = new HashSet<>();
      origin.citations().forEach(hit -> originChunks.add(hit.chunkId()));
      // Prefer a neighbor's additional source over a fragment already found by the entity name.
      neighbor.citations().stream()
          .filter(hit -> !originChunks.contains(hit.chunkId()))
          .forEach(hit -> expansion.putIfAbsent(hit.chunkId(), hit));
      relationSources.forEach(hit -> expansion.putIfAbsent(hit.chunkId(), hit));
      neighbor.citations().forEach(hit -> expansion.putIfAbsent(hit.chunkId(), hit));
      Map<String, RagSearchHit> evidence = new LinkedHashMap<>();
      relationSources.forEach(hit -> evidence.put(hit.chunkId(), hit));
      neighbor.citations().forEach(hit -> evidence.putIfAbsent(hit.chunkId(), hit));
      paths.add(
          new GraphRetrievalPath(
              List.of(
                  new GraphRetrievalPath.Node(from.id(), from.label()),
                  new GraphRetrievalPath.Node(to.id(), to.label())),
              List.of(
                  new GraphRetrievalPath.Relation(
                      source, target, text(edge.get("label")), relationSources)),
              List.copyOf(evidence.values())));
      if (paths.size() >= MAX_PATHS) break;
    }
    if (paths.isEmpty()) return baseline;
    int limit = Math.max(1, Math.min(20, requestedLimit));
    Map<String, RagSearchHit> selected = new LinkedHashMap<>();
    int baselineSlots = Math.max(1, limit - 2);
    baseline.hits().stream().limit(baselineSlots).forEach(hit -> selected.put(hit.chunkId(), hit));
    for (RagSearchHit hit : expansion.values()) {
      if (selected.size() >= limit) break;
      selected.putIfAbsent(hit.chunkId(), hit);
    }
    for (RagSearchHit hit : baseline.hits()) {
      if (selected.size() >= limit) break;
      selected.putIfAbsent(hit.chunkId(), hit);
    }
    List<RagSearchHit> numbered = new ArrayList<>();
    for (RagSearchHit hit : selected.values()) numbered.add(number(hit, numbered.size() + 1));
    List<GraphRetrievalPath> retained =
        paths.stream()
            .map(path -> path.retainCitations(numbered))
            .filter(Objects::nonNull)
            .toList();
    if (retained.isEmpty()) return baseline;
    return new RagSearchResponse(
        numbered,
        baseline.mode(),
        baseline.warning(),
        baseline.actualMode(),
        baseline.degradedReason(),
        baseline.indexVersions(),
        retained);
  }

  private static RagSearchHit number(RagSearchHit hit, int number) {
    return new RagSearchHit(
        hit.chunkId(),
        hit.documentId(),
        hit.kbId(),
        hit.title(),
        hit.content(),
        hit.page(),
        hit.ordinal(),
        hit.score(),
        "[" + number + "]",
        hit.visuals(),
        hit.indexVersion(),
        hit.documentVersion(),
        hit.parseRevision());
  }

  private static boolean matches(String query, String label) {
    String value = normalize(label);
    if (value.isEmpty()) return false;
    if (value.length() == 1
        && Character.UnicodeScript.of(value.codePointAt(0)) != Character.UnicodeScript.HAN)
      return false;
    String words = normalizeWords(label);
    if (words.matches("[a-z0-9_ ]+"))
      return Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(words) + "(?![a-z0-9_])")
          .matcher(query)
          .find();
    return normalize(query).contains(value);
  }

  private static boolean aliasesMatch(String query, Object raw) {
    return raw instanceof List<?> list
        && list.stream().anyMatch(alias -> matches(query, text(alias)));
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", "");
  }

  private static String normalizeWords(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ")
        .strip();
  }

  private static List<RagSearchHit> hits(Object raw) {
    if (!(raw instanceof List<?> list)) return List.of();
    return list.stream()
        .map(
            item ->
                item instanceof RagSearchHit hit
                    ? hit
                    : JSON.convertValue(item, RagSearchHit.class))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object raw) {
    if (!(raw instanceof List<?> list)) return List.of();
    return list.stream()
        .filter(item -> item instanceof Map<?, ?>)
        .map(item -> (Map<String, Object>) item)
        .toList();
  }

  private static String text(Object value) {
    return value == null ? "" : value.toString();
  }

  private record Concept(String id, String label, List<RagSearchHit> citations) {}
}
