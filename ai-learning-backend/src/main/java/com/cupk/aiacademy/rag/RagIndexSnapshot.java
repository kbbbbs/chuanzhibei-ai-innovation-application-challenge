package com.cupk.aiacademy.rag;

/** Identity of the actual index scanned; contains no endpoint URL, credentials or vector bytes. */
public record RagIndexSnapshot(String documentId,String indexVersion,int documentVersion,int parseRevision,
                               String embeddingModel,int dimensions,boolean normalized,String chunkRevision,
                               String parserRevision,String embeddingFingerprint) {
    public RagIndexSnapshot(String documentId,String indexVersion,int documentVersion,int parseRevision,
                            String embeddingModel,int dimensions,boolean normalized,String chunkRevision){
        this(documentId,indexVersion,documentVersion,parseRevision,embeddingModel,dimensions,normalized,chunkRevision,"legacy-unverified",null);
    }
}
