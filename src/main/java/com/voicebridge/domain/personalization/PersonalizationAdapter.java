package com.voicebridge.domain.personalization;

import java.time.LocalDateTime;
import java.util.UUID;

/** 학습 job의 성공과 별개로, 실제 serving 확인을 마친 모델만 ACTIVE로 기록한다. */
public record PersonalizationAdapter(
    UUID id,
    UUID userId,
    UUID jobId,
    PersonalizationAdapterStatus status,
    String modelVersion,
    String baseRevision,
    String artifactSha256,
    int trainingRecordingCount,
    LocalDateTime trainedAt,
    LocalDateTime activatedAt) {
  public PersonalizationAdapter {
    if (id == null || userId == null || jobId == null || status == null) {
      throw new IllegalArgumentException("Adapter identity and status are required");
    }
    if (trainingRecordingCount < 0) {
      throw new IllegalArgumentException("Training recording count cannot be negative");
    }
    if (status == PersonalizationAdapterStatus.ACTIVE
        && (modelVersion == null || modelVersion.isBlank() || activatedAt == null)) {
      throw new IllegalArgumentException(
          "Active adapter requires model version and activation time");
    }
  }
}
