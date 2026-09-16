package com.voicebridge.common.response;

/**
 * 모든 API 응답의 공통 포맷 (API 명세서 0.3절과 동일한 계약).
 *
 * <pre>
 * 성공: { "success": true,  "data": {...}, "error": null }
 * 실패: { "success": false, "data": null,  "error": { "code": "...", "message": "..." } }
 * </pre>
 */
public record ApiResponse<T>(boolean success, T data, ErrorPayload error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorPayload(code, message));
    }

    public record ErrorPayload(String code, String message) {
    }
}
