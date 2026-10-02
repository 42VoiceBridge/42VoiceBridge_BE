package com.voicebridge.port.out;

import com.voicebridge.domain.personalization.PersonalizationRecording;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PersonalizationRecordingRepositoryPort {
  void prepare(PersonalizationRecording recording);

  java.util.Optional<PersonalizationRecording> findById(UUID id);

  void markDeletionPending(UUID id);

  List<PersonalizationRecording> findExpiredUploaded(LocalDateTime cutoff);

  void markUploaded(UUID id);

  void markCleanupPending(UUID id);

  boolean claimPendingForCleanup(UUID id, LocalDateTime cutoff);

  void deletePending(UUID id);

  List<PersonalizationRecording> findPendingBefore(LocalDateTime cutoff);
}
