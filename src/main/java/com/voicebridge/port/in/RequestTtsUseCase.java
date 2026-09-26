package com.voicebridge.port.in;

import java.util.UUID;

/**
 * TTS 변환 요청 계약. API 명세서 6.1절 참고. 무효화된 confirmation으로는 요청할 수 없고(핵심 불변조건), idempotencyKey가 같으면 같은 결과를
 * 반환한다(중복 생성 방지).
 */
public interface RequestTtsUseCase {

  RequestResult request(UUID userId, UUID confirmationId, UUID idempotencyKey);

  record RequestResult(UUID ttsId, String status) {}
}
