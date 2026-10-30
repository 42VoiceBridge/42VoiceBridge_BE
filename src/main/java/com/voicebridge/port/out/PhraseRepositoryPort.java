package com.voicebridge.port.out;

import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 문구 저장·조회 계약. 목록·중복·개수 조건은 애플리케이션에서 거르지 않고 DB 조회 조건으로 처리한다. */
public interface PhraseRepositoryPort {

  /** 새 문구 저장과 음성 key 반영에 함께 쓴다. 같은 사용자·같은 카테고리에 같은 텍스트가 이미 있으면 DUPLICATE_PHRASE로 번역해 던진다. */
  Phrase save(Phrase phrase);

  Optional<Phrase> findById(UUID id);

  /** 기본 문구와 그 사용자가 추가한 문구를 만든 순서대로 돌려준다. category가 null이면 모든 카테고리. */
  List<Phrase> findAllVisibleTo(UUID userId, PhraseCategory category);

  /** 그 사용자가 같은 카테고리에 같은 텍스트를 이미 추가했으면 true. 기본 문구는 보지 않는다. */
  boolean existsOwnText(UUID ownerId, PhraseCategory category, String text);

  long countCustomByOwner(UUID ownerId);

  void deleteById(UUID id);
}
