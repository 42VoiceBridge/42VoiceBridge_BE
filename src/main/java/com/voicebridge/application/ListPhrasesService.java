package com.voicebridge.application;

import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.in.ListPhrasesUseCase;
import com.voicebridge.port.out.PhraseRepositoryPort;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문구 목록 조회. 걸러내기(다른 사용자 문구 제외, 카테고리)는 DB 조회 조건이 한다. 여기서는 순서만 정한다 — category 컬럼이 문자열이라 DB에서 정렬하면 알파벳
 * 순이 되기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListPhrasesService implements ListPhrasesUseCase {

  // 카테고리 선언 순서 → 기본 문구 먼저 → DB가 돌려준 순서(추가한 순서). sorted는 안정 정렬이라 마지막 기준이 유지된다.
  private static final Comparator<Phrase> DISPLAY_ORDER =
      Comparator.comparing(Phrase::getCategory).thenComparing(phrase -> !phrase.isDefault());

  private final PhraseRepositoryPort phraseRepositoryPort;

  @Override
  public List<PhraseView> list(UUID userId, String category) {
    PhraseCategory filter = category == null ? null : PhraseCategory.from(category);
    return phraseRepositoryPort.findAllVisibleTo(userId, filter).stream()
        .sorted(DISPLAY_ORDER)
        .map(
            phrase ->
                new PhraseView(
                    phrase.getId(),
                    phrase.getCategory().name(),
                    phrase.getText(),
                    phrase.isDefault()))
        .toList();
  }
}
