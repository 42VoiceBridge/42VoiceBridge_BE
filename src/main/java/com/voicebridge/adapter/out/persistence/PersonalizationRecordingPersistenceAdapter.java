package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.personalization.PersonalizationRecording;
import com.voicebridge.domain.personalization.PersonalizationRecordingStatus;
import com.voicebridge.port.out.PersonalizationRecordingRepositoryPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PersonalizationRecordingPersistenceAdapter
    implements PersonalizationRecordingRepositoryPort {
  private final PersonalizationRecordingJpaRepository repository;

  @Override
  public java.util.Optional<PersonalizationRecording> findById(UUID id) {
    return repository.findById(id).map(PersonalizationRecordingJpaEntity::toDomain);
  }

  @Override
  @Transactional
  public void markDeletionPending(UUID id) {
    transitionIfPresent(id, PersonalizationRecordingStatus.DELETION_PENDING);
  }

  @Override
  public List<PersonalizationRecording> findExpiredUploaded(LocalDateTime cutoff) {
    return repository
        .findByStatusAndCreatedAtBefore(PersonalizationRecordingStatus.UPLOADED.name(), cutoff)
        .stream()
        .map(PersonalizationRecordingJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<PersonalizationRecording> findTrainingCandidates(
      UUID userId, LocalDateTime now, int retentionDays) {
    if (userId == null || now == null || retentionDays <= 0) {
      throw new IllegalArgumentException("User, current time and positive retention are required");
    }
    return repository
        .findByUserIdAndStatusAndUseForTrainingTrueAndCreatedAtAfter(
            userId, PersonalizationRecordingStatus.UPLOADED.name(), now.minusDays(retentionDays))
        .stream()
        .map(PersonalizationRecordingJpaEntity::toDomain)
        .filter(recording -> recording.isTrainingCandidate(now, retentionDays))
        .toList();
  }

  @Override
  public void prepare(PersonalizationRecording recording) {
    repository.saveAndFlush(new PersonalizationRecordingJpaEntity(recording));
  }

  @Override
  @Transactional
  public void markUploaded(UUID id) {
    var next = PersonalizationRecordingStatus.UPLOADED;
    if (repository.transitionIfAllowed(
            id, next.name(), PersonalizationRecordingStatus.allowedSourceNames(next))
        != 1) {
      throw new IllegalStateException("Recording upload was already removed");
    }
  }

  @Override
  @Transactional
  public void markCleanupPending(UUID id) {
    transitionIfPresent(id, PersonalizationRecordingStatus.CLEANUP_PENDING);
  }

  @Override
  @Transactional
  public boolean claimPendingForCleanup(UUID id, LocalDateTime cutoff) {
    var next = PersonalizationRecordingStatus.CLEANUP_PENDING;
    return repository.transitionIfAllowedBefore(
            id, cutoff, next.name(), PersonalizationRecordingStatus.allowedSourceNames(next))
        == 1;
  }

  @Override
  @Transactional
  public void deletePending(UUID id) {
    repository
        .findById(id)
        .filter(r -> !PersonalizationRecordingStatus.UPLOADED.name().equals(r.getStatus()))
        .ifPresent(repository::delete);
  }

  @Override
  public List<PersonalizationRecording> findPendingBefore(LocalDateTime cutoff) {
    return repository
        .findByStatusInAndCreatedAtBefore(
            PersonalizationRecordingStatus.allowedSourceNames(
                PersonalizationRecordingStatus.CLEANUP_PENDING),
            cutoff)
        .stream()
        .map(PersonalizationRecordingJpaEntity::toDomain)
        .toList();
  }

  private void transitionIfPresent(UUID id, PersonalizationRecordingStatus next) {
    repository
        .findById(id)
        .ifPresent(
            entity -> {
              PersonalizationRecordingStatus.valueOf(entity.getStatus()).requireTransitionTo(next);
              entity.setPersistenceStatus(next.name());
            });
  }
}
