package com.cupk.aiacademy.rag;

/** Page is one-based for PDF/slides; null for formats without reliable pagination. */
public record RagSearchHit(String chunkId, String documentId, String kbId,
                           String title, String content, Integer page,
                           int ordinal, double score, String citation,
                           java.util.List<com.cupk.aiacademy.rag.visual.RagVisual> visuals,
                           String indexVersion,Integer documentVersion,Integer parseRevision) {
    public RagSearchHit {visuals=visuals==null?java.util.List.of():java.util.List.copyOf(visuals);}
    public RagSearchHit(String chunkId,String documentId,String kbId,String title,String content,Integer page,
                        int ordinal,double score,String citation) {
        this(chunkId,documentId,kbId,title,content,page,ordinal,score,citation,java.util.List.of(),null,null,null);
    }
    public RagSearchHit(String chunkId,String documentId,String kbId,String title,String content,Integer page,
                        int ordinal,double score,String citation,java.util.List<com.cupk.aiacademy.rag.visual.RagVisual> visuals) {
        this(chunkId,documentId,kbId,title,content,page,ordinal,score,citation,visuals,null,null,null);
    }
}
