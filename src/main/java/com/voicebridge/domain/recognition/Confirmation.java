package com.voicebridge.domain.recognition;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 인식 결과(Recognition) 확인 내역. TTS는 recognizedText(AI 인식 결과)가 아니라 사용자가 최종 확정한 confirmedText만 신뢰한다 — 같은
 * recognitionId로 새 확인이 생기면 이전 확인은 무효화된다.
 */
public class Confirmation {

  private final UUID id;
  private final UUID recognitionId;
  private final UUID userId;
  private final String confirmedText;
  private boolean valid;
  private final LocalDateTime createdAt;

  private Confirmation(
      UUID id,
      UUID recognitionId,
      UUID userId,
      String confirmedText,
      boolean valid,
      LocalDateTime createdAt) {
    this.id = id;
    this.recognitionId = recognitionId;
    this.userId = userId;
    this.confirmedText = confirmedText;
    this.valid = valid;
    this.createdAt = createdAt;
  }

  public static Confirmation create(UUID recognitionId, UUID userId, String confirmedText) {
    if (confirmedText == null || confirmedText.isBlank()) {
      throw new IllegalArgumentException("확인할 텍스트는 비어 있을 수 없습니다.");
    }
    return new Confirmation(
        UUID.randomUUID(), recognitionId, userId, confirmedText, true, LocalDateTime.now());
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
  public static Confirmation reconstitute(
      UUID id,
      UUID recognitionId,
      UUID userId,
      String confirmedText,
      boolean valid,
      LocalDateTime createdAt) {
    return new Confirmation(id, recognitionId, userId, confirmedText, valid, createdAt);
  }

  /** 같은 recognitionId로 새 확인이 생성될 때 이전 확인을 무효화한다. 이미 무효화된 것을 다시 호출해도 그대로 무효 상태를 유지한다(멱등). */
  public void invalidate() {
    this.valid = false;
  }

  public boolean isOwnedBy(UUID userId) {
    return this.userId.equals(userId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecognitionId() {
    return recognitionId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getConfirmedText() {
    return confirmedText;
  }

  public boolean isValid() {
    return valid;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
