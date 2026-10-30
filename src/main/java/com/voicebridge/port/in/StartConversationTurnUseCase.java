package com.voicebridge.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 대화 모드 턴 시작: 상대의 말 녹음 → 받아쓰기 → 답변 후보. API 명세서 11.1절 참고. 판정 순서는 오디오 검증(400) → 호출 상한(429) →
 * 받아쓰기(503) → 후보(LLM 실패 시 빈 목록).
 */
public interface StartConversationTurnUseCase {

  TurnResult start(StartCommand command);

  record StartCommand(UUID userId, byte[] audioBytes) {}

  record TurnResult(UUID turnId, String heardText, List<Candidate> candidates) {}

  record Candidate(UUID id, String text) {}
}
