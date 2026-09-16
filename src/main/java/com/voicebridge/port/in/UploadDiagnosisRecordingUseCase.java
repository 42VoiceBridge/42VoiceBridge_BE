package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 계약만 정의된 상태 — 구현은 백엔드 A 담당. API 명세서 2.3절 참고. 업로드 후 AI 인식 요청은 port.out.AiInferenceClient(스텁)를 통해
 * 트리거한다. TODO(백엔드 A): Recording 도메인 클래스가 아직 없음 — User/DiagnosisSession처럼 팩토리 메서드 + Rich Domain 방식으로
 * 새로 설계할 것.
 */
public interface UploadDiagnosisRecordingUseCase {

  UploadResult upload(UploadCommand command);

  record UploadCommand(
      UUID userId, UUID sessionId, UUID sentenceId, byte[] audioBytes, String fileName) {}

  record UploadResult(UUID recordingId, String status) {}
}
