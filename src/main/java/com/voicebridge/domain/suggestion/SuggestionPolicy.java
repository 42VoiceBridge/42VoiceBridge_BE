package com.voicebridge.domain.suggestion;

import com.voicebridge.domain.phrase.Phrase;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * AI가 만든 문장 후보의 규칙. AI 출력은 믿지 않고 여기서 다시 거른다 — 사용자가 고르면 그대로 문구가 되므로 문구 규칙(Phrase)을 똑같이 적용한다.
 *
 * <p>개수·호출 상한은 계획서 부록 D9의 가정값이다(팀 확정 전).
 */
public final class SuggestionPolicy {

  /** 한 번에 보여 주는 후보 수 상한. */
  public static final int MAX_CANDIDATES = 5;

  /** 사용자 한 명이 1분 동안 요청할 수 있는 횟수. 비용·남용 방어선이다. */
  public static final int MAX_CALLS_PER_MINUTE = 10;

  public static final Duration RATE_WINDOW = Duration.ofMinutes(1);

  private SuggestionPolicy() {}

  /** 문구 규칙을 통과한 후보만, 처음 나온 순서대로, 중복 없이 최대 MAX_CANDIDATES개 남긴다. null 항목은 버린다. */
  public static List<String> filter(List<String> raw) {
    if (raw == null) {
      return List.of();
    }
    LinkedHashSet<String> kept = new LinkedHashSet<>();
    for (String candidate : raw) {
      Optional<String> text = Phrase.acceptableText(candidate);
      text.ifPresent(kept::add);
      if (kept.size() == MAX_CANDIDATES) {
        break;
      }
    }
    return List.copyOf(kept);
  }
}
