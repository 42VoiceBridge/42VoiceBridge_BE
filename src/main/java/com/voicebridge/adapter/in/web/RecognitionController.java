package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.RecognitionHistoryResponse;
import com.voicebridge.adapter.in.web.dto.RecognitionResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.GetRecognitionDetailUseCase;
import com.voicebridge.port.in.GetRecognitionHistoryUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recognitions")
@RequiredArgsConstructor
public class RecognitionController {
  private final GetRecognitionHistoryUseCase getRecognitionHistoryUseCase;
  private final GetRecognitionDetailUseCase getRecognitionDetailUseCase;

  @GetMapping
  public ApiResponse<RecognitionHistoryResponse> getHistory(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return ApiResponse.success(
        RecognitionHistoryResponse.from(
            getRecognitionHistoryUseCase.getHistory(userId, page, size)));
  }

  @GetMapping("/{recognitionId}")
  public ApiResponse<RecognitionResponse> getDetail(
      @AuthenticationPrincipal UUID userId, @PathVariable("recognitionId") UUID recognitionId) {
    return ApiResponse.success(
        RecognitionResponse.from(getRecognitionDetailUseCase.getDetail(userId, recognitionId)));
  }
}
