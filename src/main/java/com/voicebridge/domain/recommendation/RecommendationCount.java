package com.voicebridge.domain.recommendation;

/** 한 번에 추천받을 문장 수 규칙. AI가 50개에서 자르므로 그보다 많이 요청해도 받을 수 없다. */
public final class RecommendationCount {

  public static final int DEFAULT = 10;
  public static final int MAX = 50;

  private RecommendationCount() {}

  /** 요청이 없으면 기본 개수, 범위를 벗어나면 거절한다. */
  public static int resolve(Integer requested) {
    if (requested == null) {
      return DEFAULT;
    }
    if (requested < 1 || requested > MAX) {
      throw new IllegalArgumentException("추천 문장 수는 1개 이상 " + MAX + "개 이하여야 합니다.");
    }
    return requested;
  }
}
