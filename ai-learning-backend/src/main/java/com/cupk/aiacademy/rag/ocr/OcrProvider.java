package com.cupk.aiacademy.rag.ocr;

public interface OcrProvider {
    OcrResult recognize(OcrRequest request);
    /** Stable service/model/prompt identity; excludes credentials and exposes no endpoint. */
    String fingerprint();
}
