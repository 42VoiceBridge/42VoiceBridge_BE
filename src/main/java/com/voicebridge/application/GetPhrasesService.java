package com.voicebridge.application;

import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.in.GetPhrasesUseCase;
import com.voicebridge.port.out.PhraseRepositoryPort;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문구 목록 조회. 다른 사용자의 문구는 DB 조회 조건에서 이미 빠진다. 여기서는 카테고리별로 묶고 순서만 정한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPhrasesService implements GetPhrasesUseCase {

  // 카테고리 안에서는 기본 문구를 먼저, 그다음 내가 추가한 문구를 추가한 순서대로 보여 준다. sorted는 안정 정렬이라 DB의 만든 순서가 유지된다.
  private static final Comparator<Phrase> DEFAULT_FIRST =
      Comparator.comparing(phrase -> !phrase.isDefault());

  private final PhraseRepositoryPort phraseRepositoryPort;

  @Override
  public List<CategoryPhrases> getPhrases(UUID userId) {
    List<Phrase> visible = phraseRepositoryPort.findAllVisibleTo(userId);
    return Arrays.stream(PhraseCategory.values())
        .map(category -> group(category, visible))
        .toList();
  }

  private static CategoryPhrases group(PhraseCategory category, List<Phrase> visible) {
    List<PhraseItem> items =
        visible.stream()
            .filter(phrase -> phrase.getCategory() == category)
            .sorted(DEFAULT_FIRST)
            .map(phrase -> new PhraseItem(phrase.getId(), phrase.getText(), !phrase.isDefault()))
            .toList();
    return new CategoryPhrases(category.name(), category.label(), items);
  }
}
