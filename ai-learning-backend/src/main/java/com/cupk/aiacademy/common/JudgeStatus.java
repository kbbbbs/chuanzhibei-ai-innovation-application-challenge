package com.cupk.aiacademy.common;

public enum JudgeStatus {
    NOT_ANSWERED(0),
    ANSWERED(1),
    AUTO_CORRECT(2),
    PENDING_MANUAL_REVIEW(3),
    MANUALLY_GRADED(4);

    private final int code;

    JudgeStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static JudgeStatus fromCode(Integer code) {
        if (code == null) {
            return NOT_ANSWERED;
        }
        for (JudgeStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return NOT_ANSWERED;
    }
}
