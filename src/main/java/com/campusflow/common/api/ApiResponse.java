package com.campusflow.common.api;

import java.time.Instant;

public record ApiResponse<T>(boolean success, String code, String message, T data, String timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return success("请求成功", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data, Instant.now().toString());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return failure(errorCode, null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.message(), data, Instant.now().toString());
    }
}
