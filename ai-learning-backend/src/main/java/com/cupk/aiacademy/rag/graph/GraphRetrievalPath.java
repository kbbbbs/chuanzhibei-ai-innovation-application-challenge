package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagSearchHit;
import java.util.*;

/** A retrieved relationship with the exact source records used to support it. */
public record GraphRetrievalPath(
    List<Node> nodes, List<Relation> relations, List<RagSearchHit> citations) {
  public record Node(String id, String label) {}

  public record Relation(String source, String target, String label, List<RagSearchHit> citations) {
    public Relation {
      citations = citations == null ? List.of() : List.copyOf(citations);
    }
  }

  public GraphRetrievalPath {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    relations = relations == null ? List.of() : List.copyOf(relations);
    citations = citations == null ? List.of() : List.copyOf(citations);
  }

  /** A path is shown only when at least one relationship's actual source was used. */
  public GraphRetrievalPath retainCitations(List<RagSearchHit> selected) {
    Map<String, RagSearchHit> allowed = new LinkedHashMap<>();
    selected.forEach(hit -> allowed.put(hit.chunkId(), hit));
    List<Relation> retained = new ArrayList<>();
    Set<String> referencedNodes = new LinkedHashSet<>();
    for (Relation relation : relations) {
      List<RagSearchHit> sources = remap(relation.citations(), allowed);
      if (sources.isEmpty()) continue;
      retained.add(new Relation(relation.source(), relation.target(), relation.label(), sources));
      referencedNodes.add(relation.source());
      referencedNodes.add(relation.target());
    }
    if (retained.isEmpty()) return null;
    return new GraphRetrievalPath(
        nodes.stream().filter(node -> referencedNodes.contains(node.id())).toList(),
        retained,
        remap(citations, allowed));
  }

  public List<RagSearchHit> allEvidence() {
    Map<String, RagSearchHit> result = new LinkedHashMap<>();
    citations.forEach(hit -> result.put(hit.chunkId(), hit));
    relations.forEach(
        relation -> relation.citations().forEach(hit -> result.put(hit.chunkId(), hit)));
    return List.copyOf(result.values());
  }

  static List<RagSearchHit> remap(List<RagSearchHit> sources, Map<String, RagSearchHit> allowed) {
    Map<String, RagSearchHit> result = new LinkedHashMap<>();
    for (RagSearchHit source : sources) {
      RagSearchHit current = allowed.get(source.chunkId());
      if (sameVersion(source, current)) result.put(current.chunkId(), current);
    }
    return List.copyOf(result.values());
  }

  public static boolean sameVersion(RagSearchHit source, RagSearchHit current) {
    return current != null
        && source.documentVersion() != null
        && source.indexVersion() != null
        && source.parseRevision() != null
        && Objects.equals(source.documentId(), current.documentId())
        && Objects.equals(source.kbId(), current.kbId())
        && Objects.equals(source.documentVersion(), current.documentVersion())
        && Objects.equals(source.indexVersion(), current.indexVersion())
        && Objects.equals(source.parseRevision(), current.parseRevision());
  }
}
