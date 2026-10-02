package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.DeletePersonalizationRecordingUseCase;
import com.voicebridge.port.out.PersonalizationRecordingRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeletePersonalizationRecordingService
    implements DeletePersonalizationRecordingUseCase {
  private final PersonalizationRecordingRepositoryPort recordings;
  private final StoragePort storage;

  @Override
  public void delete(UUID userId, UUID recordingId) {
    var recording =
        recordings
            .findById(recordingId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!recording.userId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    recordings.markDeletionPending(recordingId);
    try {
      storage.delete(recording.storageKey());
      recordings.deletePending(recordingId);
    } catch (RuntimeException e) {
      log.warn("Recording deletion queued: recordingId={}", recordingId, e);
    }
  }
}
