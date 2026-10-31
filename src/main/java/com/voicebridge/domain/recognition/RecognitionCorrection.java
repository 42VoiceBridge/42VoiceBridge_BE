package com.voicebridge.domain.recognition;

import java.time.Duration;
import java.util.Optional;

/**
 * 인식 원문을 LLM이 보정한 결과를 받아들일지 정하는 규칙. 보정은 발음 때문에 생긴 철자 오류만 고치는 용도라, 원문과 너무 다른 문장(LLM이 뜻을 지어낸 경우)은 버리고
 * 원문을 쓴다. 기준값은 실측 전 가정값이다.
 */
public final class RecognitionCorrection {

  /** 사용자당 분당 보정 호출 수. 넘으면 보정 없이 원문을 쓴다(인식은 막지 않는다). */
  public static final int MAX_CALLS_PER_MINUTE = 20;

  public static final Duration RATE_WINDOW = Duration.ofMinutes(1);

  /** 공백을 뺀 글자 기준 편집 거리 / 긴 쪽 길이의 상한. 넘으면 뜻이 바뀌었다고 보고 버린다. */
  static final double MAX_EDIT_RATIO = 0.6;

  private RecognitionCorrection() {}

  /** 받아들일 보정본을 돌려준다. 원문이 비었거나 보정본이 규칙에 맞지 않으면 빈 값이다. */
  public static Optional<String> accept(String rawText, String corrected) {
    if (rawText == null || rawText.isBlank() || corrected == null) {
      return Optional.empty();
    }
    String candidate = corrected.strip();
    if (candidate.isEmpty() || candidate.codePoints().anyMatch(Character::isISOControl)) {
      return Optional.empty();
    }
    String a = withoutSpaces(rawText);
    String b = withoutSpaces(candidate);
    int longer = Math.max(codePointLength(a), codePointLength(b));
    if (longer == 0) {
      return Optional.empty();
    }
    double ratio = (double) editDistance(a, b) / longer;
    return ratio <= MAX_EDIT_RATIO ? Optional.of(candidate) : Optional.empty();
  }

  private static String withoutSpaces(String text) {
    return text.replaceAll("\\s+", "");
  }

  private static int codePointLength(String text) {
    return text.codePointCount(0, text.length());
  }

  // 글자(코드 포인트) 단위 레벤슈타인 거리. 인식 결과는 30초 이하 발화라 길이가 짧아 O(n·m)로 충분하다.
  static int editDistance(String left, String right) {
    int[] a = left.codePoints().toArray();
    int[] b = right.codePoints().toArray();
    int[] previous = new int[b.length + 1];
    int[] current = new int[b.length + 1];
    for (int j = 0; j <= b.length; j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length; i++) {
      current[0] = i;
      for (int j = 1; j <= b.length; j++) {
        int substitution = previous[j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1);
        current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length];
  }
}
