package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.RequestTtsRequest;
import com.voicebridge.adapter.in.web.dto.TtsRequestResponse;
import com.voicebridge.adapter.in.web.dto.TtsStatusResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.GetTtsStatusUseCase;
import com.voicebridge.port.in.RequestTtsUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TTS 요청/조회 게이트. 실제 음성 합성은 아직 만들지 않았다(엔진 미정) — 요청은 항상 PENDING으로 접수되고 그 이상 진행되지 않는다. */
@RestController
@RequestMapping("/api/v1/tts")
@RequiredArgsConstructor
public class TtsController {

  private final RequestTtsUseCase requestTtsUseCase;
  private final GetTtsStatusUseCase getTtsStatusUseCase;

  @PostMapping
  public ResponseEntity<ApiResponse<TtsRequestResponse>> request(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody RequestTtsRequest request) {
    var result =
        requestTtsUseCase.request(userId, request.confirmationId(), request.idempotencyKey());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(TtsRequestResponse.from(result)));
  }

  @GetMapping("/{ttsId}")
  public ApiResponse<TtsStatusResponse> getStatus(
      @AuthenticationPrincipal UUID userId, @PathVariable("ttsId") UUID ttsId) {
    return ApiResponse.success(
        TtsStatusResponse.from(getTtsStatusUseCase.getStatus(userId, ttsId)));
  }
}
