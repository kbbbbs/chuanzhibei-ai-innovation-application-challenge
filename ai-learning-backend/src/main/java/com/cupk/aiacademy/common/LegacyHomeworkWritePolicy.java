package com.cupk.aiacademy.common;

public final class LegacyHomeworkWritePolicy {
    public static final String RETIRED_MESSAGE = "旧作业写入接口已停用，请使用统一评测（HOMEWORK）流程。";

    private LegacyHomeworkWritePolicy() {
    }

    public static <T> T rejectedResult() {
        throw new LegacyHomeworkWriteException(RETIRED_MESSAGE);
    }

    public static void rejectApiRequest() {
        throw new LegacyHomeworkWriteException(RETIRED_MESSAGE);
    }
}
