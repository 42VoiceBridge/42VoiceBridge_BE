package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.domain.personalization.PersonalizationJobStatus;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersonalizationJobPersistenceAdapter implements PersonalizationJobRepositoryPort {

  private final PersonalizationJobJpaRepository jpaRepository;

  @Override
  public PersonalizationJob save(PersonalizationJob job) {
    PersonalizationJobJpaEntity saved = jpaRepository.save(toEntity(job));
    return toDomain(saved);
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
