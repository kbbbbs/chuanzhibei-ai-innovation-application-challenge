package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.cupk.aiacademy.rag.RagSearchResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphRagEnricherTest {
    private final RagSearchHit origin = hit("a", "甲与乙相关。", "index-a");
    private final RagSearchHit neighbor = hit("b", "乙的详细定义和应用。", "index-b");
    private final RagSearchHit other = hit("c", "甲的词面补充。", "index-c");

    @Test void oneHopAddsNeighborEvidenceWithoutTheQuestionTermAndPreservesLimit() {
        var baseline = new RagSearchResponse(List.of(origin,other),"bm25",null);
        var result = GraphRagEnricher.enrich(129,"解释概念甲",graph(neighbor),baseline,
                List.of(origin,neighbor,other),3);
        assertEquals(3,result.hits().size());
        assertEquals("a",result.hits().getFirst().chunkId());
        assertTrue(result.hits().stream().anyMatch(hit -> hit.chunkId().equals("b")));
        assertFalse(neighbor.content().contains("甲"));
        assertEquals(1,result.graphPaths().size());
        assertEquals("关联",result.graphPaths().getFirst().relations().getFirst().label());
        assertEquals("[1]",result.graphPaths().getFirst().relations().getFirst().citations().getFirst().citation());
    }

    @Test void absentGraphKeepsTheOriginalRetrievalResponse() {
        var baseline = new RagSearchResponse(List.of(origin),"bm25",null);
        assertSame(baseline,GraphRagEnricher.enrich(129,"概念甲",Map.of("courseId",129,"nodes",List.of()),baseline,List.of(origin),5));
    }

    @Test void foreignCourseGraphCannotBecomeRetrievalContext() {
        var value = new java.util.LinkedHashMap<>(graph(neighbor));value.put("courseId",130);
        assertThrows(RagAccessException.class,()->GraphRagEnricher.enrich(129,"概念甲",value,
                new RagSearchResponse(List.of(origin),"bm25",null),List.of(origin,neighbor),5));
    }

    @Test void staleAndUnavailableGraphEvidenceCannotAddAnUnauthorizedChunk() {
        var stale = hit("b","旧版本私有解释。","old-index");
        var result=GraphRagEnricher.enrich(129,"概念甲",graph(stale),
                new RagSearchResponse(List.of(origin),"bm25",null),List.of(origin,neighbor),5);
        assertFalse(result.hits().stream().anyMatch(hit -> hit.chunkId().equals("b")));
        assertTrue(result.graphPaths().isEmpty());
    }

    @Test void unrelatedQuestionDoesNotExpandTheCourseScope() {
        var baseline=new RagSearchResponse(List.of(other),"bm25",null);
        assertSame(baseline,GraphRagEnricher.enrich(129,"天气如何",graph(neighbor),baseline,List.of(origin,neighbor,other),5));
    }

    @Test void shortEnglishConceptRequiresAsciiBoundariesButMayTouchChineseWords() {
        var value = new java.util.LinkedHashMap<>(graph(neighbor));
        value.put("nodes",List.of(Map.of("id","n-a","label","AI","citations",List.of(origin)),
                Map.of("id","n-b","label","概念乙","citations",List.of(neighbor))));
        var baseline = new RagSearchResponse(List.of(origin),"bm25",null);
        assertSame(baseline,GraphRagEnricher.enrich(129,"explain the course",value,baseline,List.of(origin,neighbor),5));
        assertFalse(GraphRagEnricher.enrich(129,"解释AI原理",value,baseline,List.of(origin,neighbor),5).graphPaths().isEmpty());
        assertFalse(GraphRagEnricher.enrich(129,"What is AI?",value,baseline,List.of(origin,neighbor),5).graphPaths().isEmpty());
    }

    @Test void finalAnswerMustCiteTheRelationshipSourceBeforeShowingItsPath() {
        var result=GraphRagEnricher.enrich(129,"概念甲",graph(neighbor),new RagSearchResponse(List.of(origin),"bm25",null),List.of(origin,neighbor),5);
        var path=result.graphPaths().getFirst();
        assertNull(path.retainCitations(List.of(neighbor)));
        var retained=path.retainCitations(List.of(origin));
        assertNotNull(retained);
        assertEquals(List.of("a"),retained.citations().stream().map(RagSearchHit::chunkId).toList());
    }

    private Map<String,Object> graph(RagSearchHit target) {
        return Map.of("courseId",129,"status","READY",
                "nodes",List.of(Map.of("id","n-a","label","概念甲","citations",List.of(origin)),Map.of("id","n-b","label","概念乙","citations",List.of(target))),
                "relations",List.of(Map.of("id","edge","source","n-a","target","n-b","label","关联","citations",List.of(origin))));
    }
    private RagSearchHit hit(String id,String content,String index) {
        return new RagSearchHit(id,"doc-"+id,"base","资料"+id,content,1,1,1,"[1]",List.of(),index,1,1);
    }
}
