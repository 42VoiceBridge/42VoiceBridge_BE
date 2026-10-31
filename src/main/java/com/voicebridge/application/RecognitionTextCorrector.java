package com.voicebridge.application;

import com.voicebridge.domain.recognition.RecognitionCorrection;
import com.voicebridge.port.out.CallRateLimitPort;
import com.voicebridge.port.out.LlmSuggestionPort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실사용 인식 원문을 LLM으로 보정한다(5.1). 보정은 부가 기능이라 어떤 이유로든 실패하면(꺼짐, 키 없음, 호출 상한, LLM 장애·시간 초과, 규칙에 맞지 않는
 * 보정본) 원문을 그대로 돌려준다. 인식 자체를 막지 않는다. 원문과 보정본 텍스트는 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class RecognitionTextCorrector {

  static final String FEATURE = "recognition-correction";

  private final LlmSuggestionPort llmSuggestionPort;
  private final CallRateLimitPort callRateLimitPort;
  private final boolean enabled;

  public RecognitionTextCorrector(
      LlmSuggestionPort llmSuggestionPort,
      CallRateLimitPort callRateLimitPort,
      @Value("${voicebridge.features.recognition-correction:false}") boolean enabled) {
    this.llmSuggestionPort = llmSuggestionPort;
    this.callRateLimitPort = callRateLimitPort;
    this.enabled = enabled;
  }

  /** 화면에 보여 줄 문장을 돌려준다. 보정을 받아들이지 못하면 rawText 그대로다. */
  public String correct(UUID userId, String rawText) {
    if (!enabled || rawText == null || rawText.isBlank()) {
      return rawText;
    }
    try {
      if (!callRateLimitPort.tryAcquire(
          userId,
          FEATURE,
          RecognitionCorrection.MAX_CALLS_PER_MINUTE,
          RecognitionCorrection.RATE_WINDOW)) {
        log.info("[인식 보정] 호출 상한이라 원문을 쓴다");
        return rawText;
      }
      var accepted = RecognitionCorrection.accept(rawText, llmSuggestionPort.correct(rawText));
      if (accepted.isEmpty()) {
        log.info("[인식 보정] 보정본이 규칙에 맞지 않아 원문을 쓴다");
        return rawText;
      }
      return accepted.get();
    } catch (RuntimeException e) {
      // LLM 장애(CustomException)뿐 아니라 호출 상한 저장소(Redis) 장애도 인식을 막지 않는다.
      log.warn("[인식 보정] 실패해 원문을 쓴다 cause={}", e.getClass().getSimpleName());
      return rawText;
    }
  }
}
