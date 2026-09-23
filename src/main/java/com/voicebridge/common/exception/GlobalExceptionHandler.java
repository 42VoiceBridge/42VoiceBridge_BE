package com.voicebridge.common.exception;

import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.domain.personalization.InsufficientRecordingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 모든 Controller에 공통으로 적용되는 예외 처리기. 도메인 예외 → API 명세서 0.3/0.6절 포맷으로 변환하는 그러지점은 여기 하나로 고정한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("[CustomException] {} - {}", errorCode.name(), e.getMessage());
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.name(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse(ErrorCode.VALIDATION_FAILED.getMessage());
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
        .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(), message));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(
      MethodArgumentTypeMismatchException e) {
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
        .body(
            ApiResponse.error(
                ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.getMessage()));
  }

  /**
   * 도메인 상태 전이 위반(IllegalStateException)과 검증 실패(IllegalArgumentException)를 각각 409/400으로 매핑하는 범용 핸들러.
   * 특정 유스케이스가 더 구체적인 에러 코드가 필요하면(예: INSUFFICIENT_RECORDINGS), 해당 도메인 패키지 안에 프레임워크 의존성 없는 전용 예외 클래스를
   * 만들고 여기에 전용 @ExceptionHandler를 추가할 것 — 도메인이 CustomException/ErrorCode를 직접 참조하게 하지 말 것(도메인 순수성
   * 위반).
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
    return ResponseEntity.status(ErrorCode.INVALID_STATE_TRANSITION.getStatus())
        .body(ApiResponse.error(ErrorCode.INVALID_STATE_TRANSITION.name(), e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException e) {
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
        .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(), e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("[UnhandledException]", e);
    return ResponseEntity.internalServerError()
        .body(
            ApiResponse.error(
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
  }

  @ExceptionHandler(InsufficientRecordingException.class)
  public ResponseEntity<ApiResponse<Void>> handleInsufficientRecordingException(
      InsufficientRecordingException e) {
    return ResponseEntity.status(ErrorCode.INSUFFICIENT_RECORDINGS.getStatus())
        .body(ApiResponse.error(ErrorCode.INSUFFICIENT_RECORDINGS.name(), e.getMessage()));
  }
}
