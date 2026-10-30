package com.voicebridge.domain.phrase;

import java.util.Arrays;

/**
 * 문구 카테고리. 사용자가 새로 만들 수 없고(결정 D2) 이 목록 안에서만 문구를 추가한다. 선언 순서가 화면에 보이는 순서다.
 *
 * <p>기본 카테고리·문구 목록은 기획 확정 전 초안이다(결정 D1). 값을 지우거나 이름을 바꾸면 이미 저장된 문구를 읽지 못하므로, 바꿀 때는 데이터 이전을 함께 한다.
 */
public enum PhraseCategory {
  GREETING("인사"),
  REQUEST("부탁"),
  DAILY("일상"),
  HEALTH("병원·건강"),
  EMERGENCY("긴급");

  private final String label;

  PhraseCategory(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** API로 받은 카테고리 코드를 해석한다. 대소문자를 구분하며, 없는 코드는 400으로 이어진다. */
  public static PhraseCategory from(String code) {
    if (code == null) {
      throw new IllegalArgumentException("카테고리를 선택해 주세요.");
    }
    return Arrays.stream(values())
        .filter(category -> category.name().equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 카테고리입니다."));
  }
}
