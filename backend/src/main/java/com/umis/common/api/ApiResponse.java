package com.umis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Standard API response envelope for UMIS endpoints.
 *
 * @param <T> data payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Operation successful", data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, null, errorCode, Instant.now());
    }
}
