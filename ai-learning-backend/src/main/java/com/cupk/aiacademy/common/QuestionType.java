package com.cupk.aiacademy.common;

public enum QuestionType {
    SINGLE_CHOICE("single"),
    MULTIPLE_CHOICE("multiple"),
    TRUE_FALSE("judge"),
    FILL_BLANK("blank"),
    SHORT_ANSWER("short_answer");

    private final String storageValue;

    QuestionType(String storageValue) {
        this.storageValue = storageValue;
    }

    public String getStorageValue() {
        return storageValue;
    }

    public static QuestionType fromStorageValue(String value) {
        if (value == null) {
            return SHORT_ANSWER;
        }
        String normalized = value.trim().toLowerCase();
        for (QuestionType type : values()) {
            if (type.storageValue.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return SHORT_ANSWER;
    }
}
