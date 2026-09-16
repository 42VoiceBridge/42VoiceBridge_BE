package com.voicebridge.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 계약만 정의된 상태 — 구현은 백엔드 A 담당. API 명세서 2.5절 참고. 세션 내 모든 녹음이 DONE 상태여야 호출 가능(아니면
 * INVALID_STATE_TRANSITION) — DiagnosisSession.markAnalyzed()가 이 상태 전이를 검증하는 지점이 될 것.
 */
public interface AnalyzeWeakPhonemesUseCase {

  List<WeakPhonemeView> analyze(UUID userId, UUID sessionId);

  record WeakPhonemeView(String phoneme, double errorRate, int sampleCount) {}
}
