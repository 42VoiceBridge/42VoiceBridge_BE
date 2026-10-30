package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.port.out.PhraseRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PhrasePersistenceAdapter implements PhraseRepositoryPort {

  private final PhraseJpaRepository jpaRepository;

  @Override
  @Transactional
  public Phrase save(Phrase phrase) {
    try {
      // flush까지 해서 (owner_id, text) 유니크 위반이 이 안에서 도메인 예외로 번역되게 한다.
      return toDomain(jpaRepository.saveAndFlush(toEntity(phrase)));
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.PHRASE_DUPLICATED);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Phrase> findById(UUID id) {
    return jpaRepository.findById(id).map(PhrasePersistenceAdapter::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Phrase> findAllVisibleTo(UUID userId) {
    return jpaRepository.findAllVisibleTo(userId).stream()
        .map(PhrasePersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsVisibleText(UUID userId, String text) {
    return jpaRepository.existsVisibleText(userId, text);
  }

  @Override
  @Transactional(readOnly = true)
  public long countCustomByOwner(UUID ownerId) {
    return jpaRepository.countByOwnerId(ownerId);
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  static PhraseJpaEntity toEntity(Phrase phrase) {
    return PhraseJpaEntity.builder()
        .id(phrase.getId())
        .ownerId(phrase.getOwnerId())
        .category(phrase.getCategory())
        .text(phrase.getText())
        .audioStorageKey(phrase.getAudioStorageKey())
        .createdAt(phrase.getCreatedAt())
        .build();
  }

  private static Phrase toDomain(PhraseJpaEntity entity) {
    return Phrase.reconstitute(
        entity.getId(),
        entity.getOwnerId(),
        entity.getCategory(),
        entity.getText(),
        entity.getAudioStorageKey(),
        entity.getCreatedAt());
  }
}
