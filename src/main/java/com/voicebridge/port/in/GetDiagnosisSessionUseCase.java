package com.voicebridge.port.in;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface GetDiagnosisSessionUseCase {

  GetResult getSession(UUID userId, UUID sessionId);

  record GetResult(
      UUID sessionId, String status, List<SentenceView> sentences, LocalDateTime createdAt) {}

  /**
   * 아직 녹음하지 않은 문장은 recordingId와 recordingStatus가 null이다. 같은 문장을 다시 녹음하면 Recording이 여러 개 생기므로 가장 최근
   * 것을 노출한다.
   */
  record SentenceView(UUID sentenceId, String text, UUID recordingId, String recordingStatus) {}
}
