package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.StartDiagnosisSessionResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.StartDiagnosisSessionUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 진단 세션 컨트롤러 — 현재는 "세션 시작"만 구현돼 있다. 나머지 4개는 아직 구현체(@Service)가 없어서 여기 걸면 빈을 못 찾아 빌드가 깨진다 — 의도적으로
 * 비워둠.
 *
 * <p>TODO(백엔드 A) — 구현 완료 후 여기에 매핑 추가: - GET /api/v1/diagnosis-sessions/{sessionId} →
 * GetDiagnosisSessionUseCase - POST /api/v1/diagnosis-sessions/{sessionId}/recordings →
 * UploadDiagnosisRecordingUseCase (multipart) - GET
 * /api/v1/diagnosis-sessions/{sessionId}/recordings/{recordingId}/result →
 * GetRecordingResultUseCase - GET /api/v1/diagnosis-sessions/{sessionId}/weak-phonemes →
 * AnalyzeWeakPhonemesUseCase
 */
@RestController
@RequestMapping("/api/v1/diagnosis-sessions")
@RequiredArgsConstructor
public class DiagnosisSessionController {

  private final StartDiagnosisSessionUseCase startDiagnosisSessionUseCase;

  @PostMapping
  public ResponseEntity<ApiResponse<StartDiagnosisSessionResponse>> start(
      @AuthenticationPrincipal UUID userId) {
    var result = startDiagnosisSessionUseCase.start(userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(StartDiagnosisSessionResponse.from(result)));
  }
}
