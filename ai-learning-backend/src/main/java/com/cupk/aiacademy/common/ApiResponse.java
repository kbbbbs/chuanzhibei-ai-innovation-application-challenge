// 文件: src/main/java/com/cupk/aiacademy/common/ApiResponse.java
package com.cupk.aiacademy.common;

public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "OK");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
