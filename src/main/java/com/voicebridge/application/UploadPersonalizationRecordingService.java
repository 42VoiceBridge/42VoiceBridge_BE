package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.personalization.AudioProvenance;
import com.voicebridge.domain.personalization.PersonalizationRecording;
import com.voicebridge.port.in.UploadPersonalizationRecordingUseCase;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.PersonalizationRecordingRepositoryPort;
import com.voicebridge.port.out.ShownPromptRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadPersonalizationRecordingService
    implements UploadPersonalizationRecordingUseCase {
  private static final String CONSENT_VERSION = "personalization-consent-v1";
  private final ShownPromptRepositoryPort shownPrompts;
  private final PersonalizationRecordingRepositoryPort recordings;
  private final AudioNormalizationPort normalizer;
  private final StoragePort storage;

  @Override
  public UploadResult upload(UploadCommand command) {
    if (command.userId() == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "userId가 필요합니다.");
    }
    if (command.shownPromptId() == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "shownPromptId가 필요합니다.");
    }
    if (command.promptId() == null || command.promptId().isBlank()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "promptId는 비어 있지 않은 문자열이어야 합니다.");
    }
    if (!command.storeAudio()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "녹음 저장에 동의해야 합니다: storeAudio=true");
    }
    if (command.audioBytes() == null || command.audioBytes().length == 0) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "audioFile이 비어 있습니다.");
    }
    var prompt =
        shownPrompts
            .findById(command.shownPromptId())
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!prompt.getUserId().equals(command.userId())) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    if (!prompt.getPromptId().equals(command.promptId())) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "promptId가 추천 기록의 문장과 일치하지 않습니다.");
    }
    var normalized = normalizer.normalize(command.audioBytes());
    var recording =
        PersonalizationRecording.prepare(
            command.userId(),
            prompt.getId(),
            prompt.getPromptId(),
            prompt.getText(),
            command.useForTraining(),
            CONSENT_VERSION,
            new AudioProvenance(
                normalized.metadata().sourceFormat(),
                normalized.metadata().sourceCodec(),
                normalized.metadata().sourceSampleRate(),
                normalized.metadata().sourceChannels(),
                normalized.metadata().sampleCount(),
                normalized.metadata().sourceSha256(),
                normalized.metadata().wavSha256(),
                normalized.metadata().normalizationVersion()));
    recordings.prepare(recording);
    try {
      storage.uploadAt(recording.storageKey(), normalized.wavBytes());
      recordings.markUploaded(recording.id());
      return new UploadResult(recording.id(), "UPLOADED");
    } catch (RuntimeException failure) {
      try {
        recordings.markCleanupPending(recording.id());
        storage.delete(recording.storageKey());
        recordings.deletePending(recording.id());
      } catch (RuntimeException cleanupFailure) {
        log.error(
            "Personalization upload cleanup queued: recordingId={}",
            recording.id(),
            cleanupFailure);
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }
}
