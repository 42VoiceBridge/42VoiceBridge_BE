package com.voicebridge.domain.recognition;

import java.time.LocalDateTime;
import java.util.UUID;

/** 한 번의 실사용 음성 인식 요청과 결과를 나타낸다. */
public class Recognition {

  private final UUID id;
  private final UUID userId;
  // 화면에 보여 주고 확인·TTS로 이어지는 문장. 인식 보정이 켜져 있으면 보정본이다.
  private final String recognizedText;
  // AI 서버가 돌려준 인식 원문. 보정이 없었으면 recognizedText와 같다. 화면에는 내보내지 않는다.
  private final String rawText;
  private final ModelType modelUsed;
  private final Double confidence;
  private final LocalDateTime createdAt;

  private Recognition(
      UUID id,
      UUID userId,
      String recognizedText,
      String rawText,
      ModelType modelUsed,
      Double confidence,
      LocalDateTime createdAt) {
    this.id = id;
    this.userId = userId;
    this.recognizedText = recognizedText;
    this.rawText = rawText;
    this.modelUsed = modelUsed;
    this.confidence = confidence;
    this.createdAt = createdAt;
  }

  /** 보정 없이 인식 원문을 그대로 보여 줄 때. */
  public static Recognition create(
      UUID userId, String recognizedText, ModelType modelUsed, Double confidence) {
    return create(userId, recognizedText, recognizedText, modelUsed, confidence);
  }

  /** 인식 원문(rawText)과 화면에 보여 줄 문장(recognizedText, 보정본일 수 있음)을 따로 둘 때. */
  public static Recognition create(
      UUID userId, String rawText, String recognizedText, ModelType modelUsed, Double confidence) {
    return new Recognition(
        UUID.randomUUID(),
        userId,
        recognizedText,
        rawText,
        modelUsed,
        confidence,
        LocalDateTime.now());
  }

  public static Recognition reconstitute(
      UUID id,
      UUID userId,
      String recognizedText,
      String rawText,
      ModelType modelUsed,
      Double confidence,
      LocalDateTime createdAt) {
    return new Recognition(id, userId, recognizedText, rawText, modelUsed, confidence, createdAt);
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

  public String getRawText() {
    return rawText;
  }

  public ModelType getModelUsed() {
    return modelUsed;
  }

  public Double getConfidence() {
    return confidence;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
