package com.voicebridge.port.in;

import java.util.UUID;

public interface UploadPersonalizationRecordingUseCase {
  UploadResult upload(UploadCommand command);

  record UploadCommand(
      UUID userId,
      UUID shownPromptId,
      String promptId,
      boolean storeAudio,
      boolean useForTraining,
      byte[] audioBytes) {}

  record UploadResult(UUID recordingId, String status) {}
}
