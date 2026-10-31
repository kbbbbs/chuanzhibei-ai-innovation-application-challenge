package com.cupk.aiacademy.rag;

public class RagAccessException extends RuntimeException {
    private final String code;
    public RagAccessException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
