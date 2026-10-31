package com.cupk.aiacademy.common;

public final class LegacyHomeworkWritePolicy {
    public static final String RETIRED_MESSAGE = "旧作业写入接口已停用，请使用新的作业评测流程。";

    private LegacyHomeworkWritePolicy() {
    }

    public static <T> T rejectedResult() {
        throw new LegacyHomeworkWriteException(RETIRED_MESSAGE);
    }

    public static void rejectApiRequest() {
        throw new LegacyHomeworkWriteException(RETIRED_MESSAGE);
    }
}
