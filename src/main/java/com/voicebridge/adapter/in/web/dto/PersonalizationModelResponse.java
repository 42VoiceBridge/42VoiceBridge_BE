package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetPersonalizationModelUseCase;
import java.time.LocalDateTime;

public record PersonalizationModelResponse(
    boolean hasPersonalizedModel,
    String modelVersion,
    LocalDateTime trainedAt,
    Integer trainingRecordingCount) {
  public static PersonalizationModelResponse from(
      GetPersonalizationModelUseCase.ModelStatusResult result) {
    return new PersonalizationModelResponse(
        result.hasPersonalizedModel(),
        result.modelVersion(),
        result.trainedAt(),
        result.trainingRecordingCount());
  }
}
