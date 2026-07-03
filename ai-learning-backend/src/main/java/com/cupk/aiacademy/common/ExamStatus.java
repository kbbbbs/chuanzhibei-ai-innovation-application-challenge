package com.cupk.aiacademy.common;

public enum ExamStatus {
    DRAFT(0),
    PUBLISHED(1),
    CLOSED(3);

    private final int code;

    ExamStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ExamStatus fromCode(Integer code) {
        if (code == null || code == 0) {
            return DRAFT;
        }
        if (code == 1 || code == 2) {
            return PUBLISHED;
        }
        return CLOSED;
    }
}
