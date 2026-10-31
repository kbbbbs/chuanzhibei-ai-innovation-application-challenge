package com.cupk.aiacademy.rag;

import java.util.List;

/** mode describes retrieval actually performed, never the merely configured mode. */
public record RagSearchResponse(List<RagSearchHit> hits,String mode,String warning,String actualMode,
                                 String degradedReason,List<RagIndexSnapshot> indexVersions) {
    public RagSearchResponse {hits=hits==null?List.of():List.copyOf(hits);indexVersions=indexVersions==null?List.of():List.copyOf(indexVersions);}
    public RagSearchResponse(List<RagSearchHit> hits,String mode,String warning){
        this(hits,mode,warning,"hybrid".equals(mode)?"HYBRID":"bm25".equals(mode)?"KEYWORD":"NONE",null,List.of());
    }
}
