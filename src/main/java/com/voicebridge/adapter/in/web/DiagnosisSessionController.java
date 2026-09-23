package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.StartDiagnosisSessionResponse;
import com.voicebridge.adapter.in.web.dto.UploadRecordingResponse;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.StartDiagnosisSessionUseCase;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * TODO(백엔드 A) — 구현 완료 후 여기에 매핑 추가: - GET /api/v1/diagnosis-sessions/{sessionId} →
 * GetDiagnosisSessionUseCase - GET
 * /api/v1/diagnosis-sessions/{sessionId}/recordings/{recordingId}/result →
 * GetRecordingResultUseCase - GET /api/v1/diagnosis-sessions/{sessionId}/weak-phonemes →
 * AnalyzeWeakPhonemesUseCase
 */
@RestController
@RequestMapping("/api/v1/diagnosis-sessions")
@RequiredArgsConstructor
public class DiagnosisSessionController {

  private final StartDiagnosisSessionUseCase startDiagnosisSessionUseCase;
  private final UploadDiagnosisRecordingUseCase uploadDiagnosisRecordingUseCase;

  @PostMapping
  public ResponseEntity<ApiResponse<StartDiagnosisSessionResponse>> start(
      @AuthenticationPrincipal UUID userId) {
    var result = startDiagnosisSessionUseCase.start(userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(StartDiagnosisSessionResponse.from(result)));
  }

  // AI 인식은 비동기로 돌기 때문에 여기서는 접수만 하고 202를 돌려준다. 결과는 결과 조회 API로 확인한다.
  @PostMapping(value = "/{sessionId}/recordings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<UploadRecordingResponse>> uploadRecording(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID sessionId,
      @RequestParam UUID sentenceId,
      @RequestPart("file") MultipartFile file) {

    if (file.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "녹음 파일이 비어 있습니다.");
    }

    byte[] audioBytes;
    try {
      audioBytes = file.getBytes();
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "녹음 파일을 읽지 못했습니다.");
    }

    var result =
        uploadDiagnosisRecordingUseCase.upload(
            new UploadDiagnosisRecordingUseCase.UploadCommand(
                userId, sessionId, sentenceId, audioBytes, file.getOriginalFilename()));

    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(UploadRecordingResponse.from(result)));
  }
}
