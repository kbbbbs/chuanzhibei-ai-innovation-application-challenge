package com.cupk.aiacademy.common;

public enum Difficulty {
    EASY(1),
    MEDIUM(2),
    HARD(3);

    private final int code;

    Difficulty(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Difficulty fromCode(Integer code) {
        if (code == null) {
            return EASY;
        }
        for (Difficulty difficulty : values()) {
            if (difficulty.code == code) {
                return difficulty;
            }
        }
        return EASY;
    }
}
