package com.voicebridge.application;

import com.voicebridge.port.out.PersonalizationRecordingRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PersonalizationUploadCleanup {
  private final PersonalizationRecordingRepositoryPort recordings;
  private final StoragePort storage;

  private final int retentionDays;

  public PersonalizationUploadCleanup(
      PersonalizationRecordingRepositoryPort recordings,
      StoragePort storage,
      @Value("${voicebridge.personalization.retention-days:30}") int retentionDays,
      @Value("${voicebridge.personalization.cleanup-delay-ms:300000}") long cleanupDelayMs) {
    if (retentionDays <= 0 || cleanupDelayMs <= 0) {
      throw new IllegalArgumentException(
          "Personalization retention and cleanup delay must be positive");
    }
    this.recordings = recordings;
    this.storage = storage;
    this.retentionDays = retentionDays;
  }

  @Scheduled(fixedDelayString = "${voicebridge.personalization.cleanup-delay-ms:300000}")
  public void cleanup() {
    for (var recording :
        recordings.findExpiredUploaded(LocalDateTime.now().minusDays(retentionDays))) {
      try {
        recordings.markDeletionPending(recording.id());
      } catch (RuntimeException e) {
        log.warn("Failed to expire personalization recording: recordingId={}", recording.id(), e);
      }
    }
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
    for (var recording : recordings.findPendingBefore(cutoff)) {
      try {
        if (!recordings.claimPendingForCleanup(recording.id(), cutoff)) {
          continue;
        }
        storage.delete(recording.storageKey());
        recordings.deletePending(recording.id());
      } catch (RuntimeException e) {
        log.warn("Personalization upload cleanup failed: recordingId={}", recording.id(), e);
      }
    }
  }
}
