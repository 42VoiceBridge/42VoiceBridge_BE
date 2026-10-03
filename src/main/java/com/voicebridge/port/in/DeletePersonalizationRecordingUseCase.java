package com.voicebridge.port.in;

import java.util.UUID;

public interface DeletePersonalizationRecordingUseCase {
  void delete(UUID userId, UUID recordingId);
}
