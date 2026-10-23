package edu.course.library.dto;

/** HTTP/JSON 统一响应：code 对应 HTTP 状态码，data 为业务结果。 */
public record ApiResult<T>(int code, String message, T data) {
    public static <T> ApiResult<T> ok(T data) { return new ApiResult<>(200, "success", data); }
}
