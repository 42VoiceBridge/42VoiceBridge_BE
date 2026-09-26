package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.TtsRequest;
import com.voicebridge.port.out.TtsRequestRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TtsRequestPersistenceAdapter implements TtsRequestRepositoryPort {
  private final TtsRequestJpaRepository jpaRepository;

  @Override
  @Transactional
  public TtsRequest save(TtsRequest ttsRequest) {
    try {
      // flush까지 수행해 idempotency_key 유니크 제약 위반이 어댑터 안에서 예외로 번역되게 한다.
      var saved =
          jpaRepository.saveAndFlush(
              TtsRequestJpaEntity.builder()
                  .id(ttsRequest.getId())
                  .confirmationId(ttsRequest.getConfirmationId())
                  .idempotencyKey(ttsRequest.getIdempotencyKey())
                  .status(ttsRequest.getStatus())
                  .audioUrl(ttsRequest.getAudioUrl())
                  .createdAt(ttsRequest.getCreatedAt())
                  .build());
      return toDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 요청 저장에 실패했습니다.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TtsRequest> findById(UUID id) {
    return jpaRepository.findById(id).map(TtsRequestPersistenceAdapter::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TtsRequest> findByIdempotencyKey(UUID idempotencyKey) {
    return jpaRepository
        .findByIdempotencyKey(idempotencyKey)
        .map(TtsRequestPersistenceAdapter::toDomain);
  }

  private static TtsRequest toDomain(TtsRequestJpaEntity entity) {
    return TtsRequest.reconstitute(
        entity.getId(),
        entity.getConfirmationId(),
        entity.getIdempotencyKey(),
        entity.getStatus(),
        entity.getAudioUrl(),
        entity.getCreatedAt());
  }
}
