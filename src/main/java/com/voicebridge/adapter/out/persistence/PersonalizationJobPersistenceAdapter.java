package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.domain.personalization.PersonalizationJobStatus;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 현재는 PersonalizationJobJpaEntity에 unique/FK 제약이 없어 발생 시나리오가 없는 방어 코드 — 제약 추가 시를 대비한 것. */
@Component
@RequiredArgsConstructor
public class PersonalizationJobPersistenceAdapter implements PersonalizationJobRepositoryPort {

  private final PersonalizationJobJpaRepository jpaRepository;

  @Override
  public PersonalizationJob save(PersonalizationJob job) {
    try {
      PersonalizationJobJpaEntity saved = jpaRepository.save(toEntity(job));
      return toDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "저장 중 예상치 못한 제약 위반이 발생했습니다.");
    }
  }

  @Override
  public Optional<PersonalizationJob> findById(UUID id) {
    return jpaRepository.findById(id).map(PersonalizationJobPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<PersonalizationJob> findLatestCompletedByUserId(UUID userId) {
    return jpaRepository
        .findFirstByUserIdAndStatusOrderByCompletedAtDesc(
            userId, PersonalizationJobStatus.COMPLETED)
        .map(PersonalizationJobPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<PersonalizationJob> findInProgressByUserId(UUID userId) {
    return jpaRepository
        .findFirstByUserIdAndStatus(userId, PersonalizationJobStatus.IN_PROGRESS)
        .map(PersonalizationJobPersistenceAdapter::toDomain);
  }

  private static PersonalizationJobJpaEntity toEntity(PersonalizationJob job) {
    return PersonalizationJobJpaEntity.builder()
        .id(job.getId())
        .userId(job.getUserId())
        .status(job.getStatus())
        .trainingRecordingCount(job.getTrainingRecordingCount())
        .modelVersion(job.getModelVersion())
        .modelArtifactPath(job.getModelArtifactPath())
        .failureReason(job.getFailureReason())
        .startedAt(job.getStartedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }

  private static PersonalizationJob toDomain(PersonalizationJobJpaEntity entity) {
    return PersonalizationJob.reconstitute(
        entity.getId(),
        entity.getUserId(),
        entity.getStatus(),
        entity.getTrainingRecordingCount(),
        entity.getModelVersion(),
        entity.getModelArtifactPath(),
        entity.getFailureReason(),
        entity.getStartedAt(),
        entity.getCompletedAt());
  }
}
