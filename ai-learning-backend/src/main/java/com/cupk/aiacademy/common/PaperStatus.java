package com.cupk.aiacademy.common;

public enum PaperStatus {
    DRAFT(0),
    PUBLISHED(1),
    ARCHIVED(2);

    private final int code;

    PaperStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PaperStatus fromCode(Integer code) {
        if (code == null || code == 0) {
            return DRAFT;
        }
        if (code == 1) {
            return PUBLISHED;
        }
        return ARCHIVED;
    }
}
