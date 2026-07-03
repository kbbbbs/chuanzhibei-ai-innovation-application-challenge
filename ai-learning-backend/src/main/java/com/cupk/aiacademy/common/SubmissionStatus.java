package com.cupk.aiacademy.common;

public enum SubmissionStatus {
    IN_PROGRESS(0),
    SUBMITTED(1),
    PENDING_MANUAL_REVIEW(3),
    GRADED(2);

    private final int code;

    SubmissionStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SubmissionStatus fromCode(Integer code) {
        if (code == null || code == 0) {
            return IN_PROGRESS;
        }
        if (code == 1) {
            return SUBMITTED;
        }
        if (code == 3) {
            return PENDING_MANUAL_REVIEW;
        }
        return GRADED;
    }
}
