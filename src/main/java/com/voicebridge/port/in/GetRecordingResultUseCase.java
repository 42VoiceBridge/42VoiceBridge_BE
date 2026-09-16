package com.voicebridge.port.in;

import java.util.UUID;

/** 계약만 정의된 상태 — 구현은 백엔드 A 담당. API 명세서 2.4절 참고. */
public interface GetRecordingResultUseCase {

  ResultView getResult(UUID userId, UUID sessionId, UUID recordingId);

  record ResultView(String status, String recognizedText, String answerText, Double confidence) {
    // TODO(백엔드 A): diffHighlights(오인식 구간) 필드 추가
  }
}
