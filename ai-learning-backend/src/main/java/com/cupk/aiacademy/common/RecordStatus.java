package com.cupk.aiacademy.common;

public enum RecordStatus {
    DRAFT(0),
    ACTIVE(1),
    DISABLED(2);

    private final int code;

    RecordStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static RecordStatus fromCode(Integer code) {
        if (code == null) {
            return ACTIVE;
        }
        for (RecordStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return ACTIVE;
    }
}
