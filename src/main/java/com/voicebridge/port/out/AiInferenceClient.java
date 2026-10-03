package com.voicebridge.port.out;

import com.voicebridge.domain.recognition.ModelType;
import java.util.UUID;

/** AI 추론 호출 포트. 제품의 현재 전송 방식은 HTTP이며, 실제 선택 모델은 응답에 별도로 담는다. */
public interface AiInferenceClient {

  RecognitionResult recognize(byte[] audioBytes, ModelType modelType, UUID userId);

  record RecognitionResult(String recognizedText, Double confidence, ModelType actualModelType) {
    public RecognitionResult(String recognizedText, Double confidence) {
      this(recognizedText, confidence, null);
    }
    // TODO: phonemeAlignments(음소 정렬 정보) 추가 — 취약 음소 분석에 필요
  }
}
