package com.voicebridge.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 문구 목록 조회 계약. API 명세서 10.1절 참고. 기본 문구와 본인이 추가한 문구만 돌려주고, 다른 사용자의 문구는 오지 않는다.
 *
 * <p>순서: 카테고리 선언 순서 → 카테고리 안에서는 기본 문구 먼저 → 추가한 순서.
 */
public interface ListPhrasesUseCase {

  /** category가 null이면 모든 카테고리. 없는 카테고리 코드는 400으로 이어진다. */
  List<PhraseView> list(UUID userId, String category);

  record PhraseView(UUID id, String category, String text, boolean isDefault) {}
}
