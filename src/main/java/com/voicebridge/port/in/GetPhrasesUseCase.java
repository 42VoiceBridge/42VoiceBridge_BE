package com.voicebridge.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 문구 목록 조회 계약. API 명세서 10.1절 참고. 기본 문구와 본인이 추가한 문구를 카테고리별로 묶어 돌려준다. 문구가 없는 카테고리도 빈 목록으로 포함한다(그
 * 카테고리에 추가할 수 있게).
 */
public interface GetPhrasesUseCase {

  List<CategoryPhrases> getPhrases(UUID userId);

  record CategoryPhrases(String category, String label, List<PhraseItem> phrases) {}

  record PhraseItem(UUID phraseId, String text, boolean custom) {}
}
