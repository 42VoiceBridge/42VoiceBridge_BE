package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 실사용 음성 인식 계약. 완료된 개인화 학습이 있으면 개인화 모델을 요청하고, 없으면 기본 모델을 요청한다. AI 호출 실패는
 * AI_INFERENCE_UNAVAILABLE(503)로 처리하며 장애 시 재시도하지 않는다.
 */
public interface RecognizeSpeechUseCase {

  RecognizeResult recognize(UUID userId, byte[] audioBytes, String fileName);

  record RecognizeResult(
      UUID recognitionId, String recognizedText, String modelUsed, Double confidence) {}
}
