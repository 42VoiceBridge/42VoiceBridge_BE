package com.voicebridge.port.in;

import java.util.List;
import java.util.UUID;

/**
 * API 명세서 3.1절. 문장 선택은 AI가 하고, 백엔드는 제안한 문장을 기록해 전달한다(AI 계약 §1, §3.6). 호출할 때마다 새 문장을 받아 기록을 남기므로 조회가
 * 아니라 생성이다.
 */
public interface RecommendSentencesUseCase {

  /** count가 null이면 기본 개수(RecommendationCount.DEFAULT)를 쓴다. */
  RecommendationResult recommend(UUID userId, Integer count);

  record RecommendationResult(List<RecommendedSentence> sentences) {}

  /** promptId는 AI 문장 풀의 ID다. 개인화 녹음 업로드가 어떤 문장을 읽었는지 이 값으로 연결한다. */
  record RecommendedSentence(UUID shownPromptId, String promptId, String text) {}
}
