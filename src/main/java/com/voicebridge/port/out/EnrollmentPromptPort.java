package com.voicebridge.port.out;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** AI 문장 풀에서 다음 등록 문장을 받는다(AI 계약 §3.6). 어떤 문장을 고를지는 AI가 정한다. */
public interface EnrollmentPromptPort {

  /**
   * seed는 같은 입력이면 같은 결과가 나오게 하는 값이라, 호출마다 바꾸지 않으면 매번 같은 문장이 나온다. excludePromptIds의 문장은 고르지 않는다. AI를
   * 쓸 수 없으면 CustomException(AI_INFERENCE_UNAVAILABLE)을 던진다.
   */
  PromptBatch nextPrompts(UUID userId, int count, long seed, Collection<String> excludePromptIds);

  /** strategy·strategyVersion·seed는 보여준 문장 기록에 그대로 남긴다. */
  record PromptBatch(String strategy, String strategyVersion, long seed, List<Prompt> prompts) {}

  record Prompt(String promptId, String text) {}
}
