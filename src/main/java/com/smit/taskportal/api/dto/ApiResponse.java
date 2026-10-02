package com.smit.taskportal.api.dto;

/**
 * Uniform envelope for every JSON response.
 *
 * <pre>{@code
 * { "success": true,  "message": "OK",     "data": { ... } }
 * { "success": false, "message": "...",    "data": null     }
 * }</pre>
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
