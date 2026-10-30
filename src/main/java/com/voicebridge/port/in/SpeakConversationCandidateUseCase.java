package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 대화 모드 후보 말하기. API 명세서 11.2절 참고. 이 턴에서 서버가 발급한 후보만 합성하며, 합성한 음성(mp3)은 저장하지 않고 바로 돌려준다. 판정 순서는 턴
 * 없음·만료(404) → 다른 사용자(403) → 후보 없음(404) → 호출 상한(429) → 합성(503).
 */
public interface SpeakConversationCandidateUseCase {

  byte[] speak(SpeakCommand command);

  record SpeakCommand(UUID userId, UUID turnId, UUID candidateId) {}
}
