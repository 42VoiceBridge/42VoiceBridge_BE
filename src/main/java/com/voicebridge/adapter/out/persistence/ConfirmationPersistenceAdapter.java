package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.Confirmation;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConfirmationPersistenceAdapter implements ConfirmationRepositoryPort {
  private final ConfirmationJpaRepository jpaRepository;

  @Override
  @Transactional
  public Confirmation save(Confirmation confirmation) {
    try {
      // flush까지 수행해 제약 위반이 어댑터 안에서 예외로 번역되게 한다.
      var saved =
          jpaRepository.saveAndFlush(
              ConfirmationJpaEntity.builder()
                  .id(confirmation.getId())
                  .recognitionId(confirmation.getRecognitionId())
                  .userId(confirmation.getUserId())
                  .confirmedText(confirmation.getConfirmedText())
                  .valid(confirmation.isValid())
                  .createdAt(confirmation.getCreatedAt())
                  .build());
      return toDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "확인 내역 저장에 실패했습니다.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Confirmation> findById(UUID id) {
    return jpaRepository.findById(id).map(ConfirmationPersistenceAdapter::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Confirmation> findValidByRecognitionId(UUID recognitionId) {
    return jpaRepository
        .findByRecognitionIdAndValidTrue(recognitionId)
        .map(ConfirmationPersistenceAdapter::toDomain);
  }

  private static Confirmation toDomain(ConfirmationJpaEntity entity) {
    return Confirmation.reconstitute(
        entity.getId(),
        entity.getRecognitionId(),
        entity.getUserId(),
        entity.getConfirmedText(),
        entity.isValid(),
        entity.getCreatedAt());
  }
}
