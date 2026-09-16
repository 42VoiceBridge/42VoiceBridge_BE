package com.voicebridge.port.out;

import java.util.UUID;

/**
 * Java-Python(Whisper) 추론 계층 호출 계약. 구현 방식(JPyRust in-process vs FastAPI HTTP)은 PoC 결과에 따라 결정 — 어느
 * 쪽으로 가든 이 인터페이스는 바뀌지 않는다(요구사항명세서 5장). PoC 전까지는 adapter.out.ai에 스텁만 둔다. 진단세션(A)과 AI 연동(B) 양쪽이 공유하는
 * 계약이라 미리 확정해서 여기 둔다.
 */
public interface AiInferenceClient {

  RecognitionResult recognize(byte[] audioBytes, ModelType modelType, UUID userId);

  enum ModelType {
    BASE_ADAPTED,
    PERSONALIZED
  }

  record RecognitionResult(String recognizedText, double confidence) {
    // TODO: phonemeAlignments(음소 정렬 정보) 추가 — 취약 음소 분석에 필요
  }
}
