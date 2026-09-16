package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 계약만 정의된 상태 — 구현은 백엔드 B 담당. API 명세서 4.1절 참고. feature/diagnosis-session의
 * UploadDiagnosisRecordingUseCase와 업로드 패턴이 유사하다 — 참고할 것.
 */
public interface UploadPersonalizationRecordingUseCase {

  UploadResult upload(UploadCommand command);

  record UploadCommand(UUID userId, UUID sentenceId, byte[] audioBytes, String fileName) {}

  record UploadResult(UUID recordingId, String status) {}
}
