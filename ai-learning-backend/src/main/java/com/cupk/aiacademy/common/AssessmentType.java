package com.cupk.aiacademy.common;

public enum AssessmentType {
    EXAM,
    HOMEWORK;

    public static AssessmentType fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return EXAM;
        }
        try {
            return AssessmentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("不支持的评测类型: " + value);
        }
    }
}
