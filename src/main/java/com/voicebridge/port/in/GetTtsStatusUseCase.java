package com.voicebridge.port.in;

import java.util.UUID;

/** TTS 요청 상태 조회 계약. API 명세서 6.2절 참고. */
public interface GetTtsStatusUseCase {

  TtsStatusResult getStatus(UUID userId, UUID ttsId);

  record TtsStatusResult(UUID ttsId, String status, String audioUrl) {}
}
