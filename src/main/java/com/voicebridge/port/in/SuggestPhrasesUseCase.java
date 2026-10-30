package com.voicebridge.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 문장 확장·문구 추천 계약. API 명세서 10.6절 참고. 후보는 저장하지 않고, 사용자가 고르면 문구 추가(10.2)로 저장한 뒤 말한다. 입력 검증(400) → 호출
 * 상한(429) → LLM(503) 순서로 판정한다.
 */
public interface SuggestPhrasesUseCase {

  SuggestResult suggest(SuggestCommand command);

  record SuggestCommand(UUID userId, String text, String category) {}

  record SuggestResult(List<String> candidates) {}
}
