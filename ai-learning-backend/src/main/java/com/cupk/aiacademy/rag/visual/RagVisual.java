package com.cupk.aiacademy.rag.visual;

import java.util.List;

/** Public metadata only. Image bytes and model credentials never enter retrieval/history JSON. */
public record RagVisual(String id,String documentId,int documentVersion,int parseRevision,int page,int ordinal,
                        String kind,String caption,String context,String questionNumber,String origin,
                        boolean needsReview,String imageUrl,int width,int height,List<Integer> bbox) {
    public RagVisual {bbox=bbox==null?List.of():List.copyOf(bbox);}
}
