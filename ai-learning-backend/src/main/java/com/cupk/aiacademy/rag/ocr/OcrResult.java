package com.cupk.aiacademy.rag.ocr;

/** Provider output is retained separately from searchable text. Missing confidence stays null. */
public record OcrResult(String rawText,String text,Double confidence,String model,long latencyMs,boolean needsReview,String errorCode,
                        String priorRawText,Double retryScale) {
    public OcrResult(String rawText,String text,Double confidence,String model,long latencyMs,boolean needsReview,String errorCode) {
        this(rawText,text,confidence,model,latencyMs,needsReview,errorCode,null,null);
    }
    public OcrResult(String rawText,String text,Double confidence,String model,long latencyMs) {
        this(rawText,text,confidence,model,latencyMs,false,null);
    }
    public OcrResult(String rawText,String text,Double confidence,String model,long latencyMs,boolean needsReview) {
        this(rawText,text,confidence,model,latencyMs,needsReview,null);
    }
}
