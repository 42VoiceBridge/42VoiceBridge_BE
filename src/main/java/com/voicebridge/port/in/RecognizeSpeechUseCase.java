package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 계약만 정의된 상태 — 구현은 백엔드 B 담당. API 명세서 5.1절 참고. 개인화 모델이 있으면 우선 사용하고, 없거나 장애 시 기본 모델로 폴백한다(NFR-4) —
 * PersonalizationJobRepositoryPort.findLatestCompletedByUserId()로 보유 여부 확인 후
 * port.out.AiInferenceClient를 호출한다.
 */
public interface RecognizeSpeechUseCase {

  RecognizeResult recognize(UUID userId, byte[] audioBytes, String fileName);

  record RecognizeResult(
      UUID recognitionId, String recognizedText, String modelUsed, double confidence) {}
}
