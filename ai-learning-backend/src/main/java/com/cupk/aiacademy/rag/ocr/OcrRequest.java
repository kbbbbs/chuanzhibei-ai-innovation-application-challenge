package com.cupk.aiacademy.rag.ocr;

/** A single rendered page or slide; language/requestId are provenance hints, never model overrides. */
public record OcrRequest(byte[] image,String mimeType,Integer pageNumber,String language,String requestId,boolean documentMode) {
    public OcrRequest(byte[] image,String mimeType,Integer pageNumber,String language,String requestId) {
        this(image,mimeType,pageNumber,language,requestId,false);
    }
}
