package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.out.PhraseRepositoryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
      // flush까지 해서 (owner_id, category, text_sha256) 유니크 위반이 이 안에서 도메인 예외로 번역되게 한다.
      return toDomain(jpaRepository.saveAndFlush(toEntity(phrase)));
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.DUPLICATE_PHRASE);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Phrase> findById(UUID id) {
    return jpaRepository.findById(id).map(PhrasePersistenceAdapter::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Phrase> findAllVisibleTo(UUID userId, PhraseCategory category) {
    return jpaRepository.findAllVisibleTo(userId, category).stream()
        .map(PhrasePersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsOwnText(UUID ownerId, PhraseCategory category, String text) {
    return jpaRepository.existsByOwnerIdAndCategoryAndTextSha256(ownerId, category, sha256(text));
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
        .textSha256(sha256(phrase.getText()))
        .audioStorageKey(phrase.getAudioStorageKey())
        .createdAt(phrase.getCreatedAt())
        .build();
  }

  // 중복 판단용 저장 형식이라 도메인이 아니라 여기서 만든다. 소문자 16진수 64자.
  static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // 모든 자바 런타임이 SHA-256을 제공해야 한다(자바 표준). 여기로 오면 런타임이 망가진 것이다.
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
    }
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
