package com.cupk.aiacademy.rag;

import java.util.List;
import com.cupk.aiacademy.rag.graph.GraphRetrievalPath;

/** mode describes retrieval actually performed, never the merely configured mode. */
public record RagSearchResponse(List<RagSearchHit> hits,String mode,String warning,String actualMode,
                                 String degradedReason,List<RagIndexSnapshot> indexVersions,
                                 List<GraphRetrievalPath> graphPaths) {
    public RagSearchResponse {hits=hits==null?List.of():List.copyOf(hits);indexVersions=indexVersions==null?List.of():List.copyOf(indexVersions);graphPaths=graphPaths==null?List.of():List.copyOf(graphPaths);}
    public RagSearchResponse(List<RagSearchHit> hits,String mode,String warning,String actualMode,
                             String degradedReason,List<RagIndexSnapshot> indexVersions) {
        this(hits,mode,warning,actualMode,degradedReason,indexVersions,List.of());
    }
    public RagSearchResponse(List<RagSearchHit> hits,String mode,String warning){
        this(hits,mode,warning,"hybrid".equals(mode)?"HYBRID":"bm25".equals(mode)?"KEYWORD":"NONE",null,List.of());
    }
}
