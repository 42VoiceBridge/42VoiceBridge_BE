package com.voicebridge.domain.recognition;

import java.time.LocalDateTime;
import java.util.UUID;

/** 한 번의 실사용 음성 인식 요청과 결과를 나타낸다. */
public class Recognition {

  private final UUID id;
  private final UUID userId;
  private final String recognizedText;
  private final ModelType modelUsed;
  private final double confidence;
  private final LocalDateTime createdAt;

  private Recognition(
      UUID id,
      UUID userId,
      String recognizedText,
      ModelType modelUsed,
      double confidence,
      LocalDateTime createdAt) {
    this.id = id;
    this.userId = userId;
    this.recognizedText = recognizedText;
    this.modelUsed = modelUsed;
    this.confidence = confidence;
    this.createdAt = createdAt;
  }

  public static Recognition create(
      UUID userId, String recognizedText, ModelType modelUsed, double confidence) {
    return new Recognition(
        UUID.randomUUID(), userId, recognizedText, modelUsed, confidence, LocalDateTime.now());
  }

  public static Recognition reconstitute(
      UUID id,
      UUID userId,
      String recognizedText,
      ModelType modelUsed,
      double confidence,
      LocalDateTime createdAt) {
    return new Recognition(id, userId, recognizedText, modelUsed, confidence, createdAt);
  }

  public boolean isOwnedBy(UUID userId) {
    return this.userId.equals(userId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getRecognizedText() {
    return recognizedText;
  }

  public ModelType getModelUsed() {
    return modelUsed;
  }

  public double getConfidence() {
    return confidence;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
