package com.voicebridge.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 인식 결과 확인 계약. API 명세서 6.0절 참고. 같은 recognitionId로 새 확인이 생기면 이전 확인은 자동으로 무효화된다 — TTS는 이 확인
 * 내역(confirmedText)만 신뢰한다.
 */
public interface ConfirmRecognitionUseCase {

  ConfirmResult confirm(UUID userId, UUID recognitionId, String confirmedText);

  record ConfirmResult(UUID confirmationId, String confirmedText, LocalDateTime confirmedAt) {}
}
