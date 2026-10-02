package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.personalization.PersonalizationRecording;
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
    repository.findById(id).ifPresent(PersonalizationRecordingJpaEntity::markDeletionPending);
  }

  @Override
  public List<PersonalizationRecording> findExpiredUploaded(LocalDateTime cutoff) {
    return repository.findByStatusAndCreatedAtBefore("UPLOADED", cutoff).stream()
        .map(PersonalizationRecordingJpaEntity::toDomain)
        .toList();
  }

  @Override
  public void prepare(PersonalizationRecording recording) {
    repository.saveAndFlush(new PersonalizationRecordingJpaEntity(recording));
  }

  @Override
  @Transactional
  public void markUploaded(UUID id) {
    if (repository.markUploadedIfPreparing(id) != 1) {
      throw new IllegalStateException("Recording upload was already removed");
    }
  }

  @Override
  @Transactional
  public void markCleanupPending(UUID id) {
    repository.findById(id).ifPresent(PersonalizationRecordingJpaEntity::markCleanupPending);
  }

  @Override
  @Transactional
  public boolean claimPendingForCleanup(UUID id, LocalDateTime cutoff) {
    return repository.claimPendingForCleanup(id, cutoff) == 1;
  }

  @Override
  @Transactional
  public void deletePending(UUID id) {
    repository
        .findById(id)
        .filter(r -> !"UPLOADED".equals(r.getStatus()))
        .ifPresent(repository::delete);
  }

  @Override
  public List<PersonalizationRecording> findPendingBefore(LocalDateTime cutoff) {
    return repository
        .findByStatusInAndCreatedAtBefore(
            List.of("PREPARING", "CLEANUP_PENDING", "DELETION_PENDING"), cutoff)
        .stream()
        .map(PersonalizationRecordingJpaEntity::toDomain)
        .toList();
  }
}
