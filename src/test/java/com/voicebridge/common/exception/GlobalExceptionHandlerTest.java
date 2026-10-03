package com.voicebridge.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.domain.personalization.InsufficientRecordingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void IllegalStateException은_409_INVALID_STATE_TRANSITION으로_변환된다() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleIllegalStateException(new IllegalStateException("테스트 메시지"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_STATE_TRANSITION");
    assertThat(response.getBody().error().message()).isEqualTo("테스트 메시지");
  }

  @Test
  void IllegalArgumentException은_400_VALIDATION_FAILED로_변환된다() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleIllegalArgumentException(new IllegalArgumentException("테스트 메시지"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.getBody().error().message()).isEqualTo("테스트 메시지");
  }

  @Test
  void 녹음_부족은_422와_전용_오류코드로_변환된다() {
    var exception = new InsufficientRecordingException();
    var response = handler.handleInsufficientRecordingException(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().success()).isFalse();
    assertThat(response.getBody().data()).isNull();
    assertThat(response.getBody().error().code()).isEqualTo("INSUFFICIENT_RECORDINGS");
    assertThat(response.getBody().error().message()).isEqualTo(exception.getMessage());
  }
}
