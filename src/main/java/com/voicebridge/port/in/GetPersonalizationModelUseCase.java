package com.voicebridge.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

public interface GetPersonalizationModelUseCase {

  ModelStatusResult getModelStatus(UUID userId);

  record ModelStatusResult(
      boolean hasPersonalizedModel,
      String modelVersion,
      LocalDateTime trainedAt,
      Integer trainingRecordingCount) {}
}
