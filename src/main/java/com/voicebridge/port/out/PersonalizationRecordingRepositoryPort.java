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

  /** 동의와 저장 상태만으로는 충분하지 않으므로 검토 메타데이터까지 검사한다. */
  List<PersonalizationRecording> findTrainingCandidates(
      UUID userId, LocalDateTime now, int retentionDays);

  void markUploaded(UUID id);

  void markCleanupPending(UUID id);

  boolean claimPendingForCleanup(UUID id, LocalDateTime cutoff);

  void deletePending(UUID id);

  List<PersonalizationRecording> findPendingBefore(LocalDateTime cutoff);
}
