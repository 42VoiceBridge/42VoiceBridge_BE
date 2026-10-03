package com.voicebridge.common.exception;

import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.domain.personalization.InsufficientRecordingException;
import com.voicebridge.port.out.AudioProcessingException;
import com.voicebridge.port.out.InvalidAudioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 Controller에 공통으로 적용되는 예외 처리기. 도메인 예외 → API 명세서 0.3/0.6절 포맷으로 변환하는 그러지점은 여기 하나로 고정한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(InvalidAudioException.class)
  public ResponseEntity<ApiResponse<Void>> handleInvalidAudio(InvalidAudioException e) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(), e.getMessage()));
  }

  @ExceptionHandler(AudioProcessingException.class)
  public ResponseEntity<ApiResponse<Void>> handleAudioProcessing(AudioProcessingException e) {
    if (e.reason() == AudioProcessingException.Reason.CAPACITY
        || e.reason() == AudioProcessingException.Reason.TIMEOUT) {
      log.warn("Audio processing temporarily unavailable: reason={}", e.reason());
      return ResponseEntity.status(ErrorCode.AUDIO_PROCESSING_UNAVAILABLE.getStatus())
          .body(
              ApiResponse.error(
                  ErrorCode.AUDIO_PROCESSING_UNAVAILABLE.name(),
                  ErrorCode.AUDIO_PROCESSING_UNAVAILABLE.getMessage()));
    }
    log.error("Audio processing infrastructure failure", e);
    return ResponseEntity.internalServerError()
        .body(
            ApiResponse.error(
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(), "녹음 업로드 크기 제한을 초과했습니다."));
  }

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

  // 필수 쿼리 파라미터나 multipart 파트가 빠진 요청은 클라이언트 실수다. 전용 처리가 없으면 맨 아래 Exception 핸들러로 떨어져
  // 500이 나가는데, 그러면 프론트는 자기 요청이 틀린 게 아니라 서버가 고장 난 것으로 보게 된다.
  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleMissingRequestValue(Exception e) {
    String name =
        e instanceof MissingServletRequestPartException part
            ? part.getRequestPartName()
            : ((MissingServletRequestParameterException) e).getParameterName();
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
        .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(), "필수 요청 값이 없습니다: " + name));
  }

  /**
   * 도메인 상태 전이 위반(IllegalStateException)과 검증 실패(IllegalArgumentException)를 각각 409/400으로 매핑하는 범용 핸들러.
   * 특정 유스케이스가 더 구체적인 에러 코드가 필요하면(예: INSUFFICIENT_RECORDINGS), 해당 도메인 패키지 안에 프레임워크 의존성 없는 전용 예외 클래스를
   * 만들고 여기에 전용 @ExceptionHandler를 추가할 것 — 도메인이 CustomException/ErrorCode를 직접 참조하게 하지 말 것(도메인 순수성
   * 위반).
   */
  // 아래 넷은 클라이언트 요청 실수다. 처리하지 않으면 마지막 Exception 처리로 떨어져 500(서버 고장)처럼 보인다.

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
    return error(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
        .allow(supportedMethods(e))
        .body(
            ApiResponse.error(
                ErrorCode.METHOD_NOT_ALLOWED.name(), ErrorCode.METHOD_NOT_ALLOWED.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
    return error(ErrorCode.VALIDATION_FAILED, "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e) {
    return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
  }

  // Allow 헤더로 이 주소가 받는 메서드를 알려준다(HTTP 405 규약).
  private static HttpMethod[] supportedMethods(HttpRequestMethodNotSupportedException e) {
    return e.getSupportedHttpMethods() == null
        ? new HttpMethod[0]
        : e.getSupportedHttpMethods().toArray(new HttpMethod[0]);
  }

  private static ResponseEntity<ApiResponse<Void>> error(ErrorCode code, String message) {
    return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code.name(), message));
  }

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
