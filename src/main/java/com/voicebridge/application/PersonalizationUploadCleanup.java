package com.voicebridge.application;

import com.voicebridge.port.out.PersonalizationRecordingRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonalizationUploadCleanup {
  private final PersonalizationRecordingRepositoryPort recordings;
  private final StoragePort storage;

  @org.springframework.beans.factory.annotation.Value(
      "${voicebridge.personalization.retention-days:30}")
  private int retentionDays;

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
