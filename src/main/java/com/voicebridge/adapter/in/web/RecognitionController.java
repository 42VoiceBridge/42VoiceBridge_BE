package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.ConfirmRecognitionRequest;
import com.voicebridge.adapter.in.web.dto.ConfirmationResponse;
import com.voicebridge.adapter.in.web.dto.RecognitionHistoryResponse;
import com.voicebridge.adapter.in.web.dto.RecognitionResponse;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.ConfirmRecognitionUseCase;
import com.voicebridge.port.in.GetRecognitionDetailUseCase;
import com.voicebridge.port.in.GetRecognitionHistoryUseCase;
import com.voicebridge.port.in.RecognizeSpeechUseCase;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recognitions")
@RequiredArgsConstructor
public class RecognitionController {
  private final RecognizeSpeechUseCase recognizeSpeechUseCase;
  private final GetRecognitionHistoryUseCase getRecognitionHistoryUseCase;
  private final GetRecognitionDetailUseCase getRecognitionDetailUseCase;
  private final ConfirmRecognitionUseCase confirmRecognitionUseCase;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<RecognitionResponse>> recognize(
      @AuthenticationPrincipal UUID userId, @RequestPart("audioFile") MultipartFile audioFile) {
    if (audioFile.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "녹음 파일이 비어 있습니다.");
    }

    byte[] audioBytes;
    try {
      audioBytes = audioFile.getBytes();
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "녹음 파일을 읽지 못했습니다.");
    }

    var result =
        recognizeSpeechUseCase.recognize(userId, audioBytes, audioFile.getOriginalFilename());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(RecognitionResponse.from(result)));
  }

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

  @PostMapping("/{recognitionId}/confirm")
  public ResponseEntity<ApiResponse<ConfirmationResponse>> confirm(
      @AuthenticationPrincipal UUID userId,
      @PathVariable("recognitionId") UUID recognitionId,
      @Valid @RequestBody ConfirmRecognitionRequest request) {
    var result = confirmRecognitionUseCase.confirm(userId, recognitionId, request.confirmedText());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(ConfirmationResponse.from(result)));
  }
}
