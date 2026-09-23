package com.voicebridge.adapter.out.ai;

import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 로컬 개발 전용. 실제 Whisper 없이 업로드 → 인식 → 결과 조회 흐름을 끝까지 돌려보기 위한 대역이다.
@Slf4j
@Component
@Profile("local")
public class StubAiInferenceClient implements AiInferenceClient {

  private static final String STUB_TEXT = "오늘 날씨가 좋습니다.";
  private static final double STUB_CONFIDENCE = 0.85;

  // 즉시 응답하면 PROCESSING 상태를 관찰할 수 없어 비동기 흐름이 제대로 도는지 확인하기 어렵다.
  private static final long SIMULATED_LATENCY_MS = 2000;

  @Override
  public RecognitionResult recognize(byte[] audioBytes, ModelType modelType, UUID userId) {
    log.info(
        "[스텁 인식] 실제 AI를 호출하지 않는다. bytes={} modelType={} userId={}",
        audioBytes.length,
        modelType,
        userId);
    try {
      Thread.sleep(SIMULATED_LATENCY_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return new RecognitionResult(STUB_TEXT, STUB_CONFIDENCE);
  }
}
