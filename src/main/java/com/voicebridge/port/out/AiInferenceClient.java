package com.voicebridge.port.out;

import com.voicebridge.domain.recognition.ModelType;
import java.util.UUID;

/**
 * Java-Python(Whisper) 추론 계층 호출 계약. 구현 방식(JPyRust in-process vs FastAPI HTTP)은 PoC 결과에 따라 결정 — 어느
 * 쪽으로 가든 이 인터페이스는 바뀌지 않는다(요구사항명세서 5장). PoC 전까지는 adapter.out.ai에 스텁만 둔다.
 *
 * <p>[수정 이력] ModelType을 이 인터페이스 안에 직접 정의하지 않고 domain.recognition.ModelType을 그대로 참조하도록 변경(SSOT) —
 * Recognition 도메인도 동일한 값 집합을 쓰기 때문에 두 곳에서 따로 정의하면 나중에 값이 어긋날 수 있다.
 */
public interface AiInferenceClient {

  RecognitionResult recognize(byte[] audioBytes, ModelType modelType, UUID userId);

  record RecognitionResult(String recognizedText, double confidence) {
    // TODO: phonemeAlignments(음소 정렬 정보) 추가 — 취약 음소 분석에 필요
  }
}
