package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetPersonalizationTrainingStatusUseCase.TrainingStatusResult;
import java.time.LocalDateTime;
import java.util.UUID;

public record PersonalizationTrainingStatusResponse(
    UUID jobId,
    String status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String failureReason) {

  public static PersonalizationTrainingStatusResponse from(TrainingStatusResult result) {
    return new PersonalizationTrainingStatusResponse(
        result.jobId(),
        result.status(),
        result.startedAt(),
        result.completedAt(),
        result.failureReason());
  }
}
