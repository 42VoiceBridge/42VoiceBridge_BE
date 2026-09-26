package com.voicebridge.domain.recognition;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TTS(텍스트→음성) 요청. 실제 합성 엔진이 아직 미정이라 이번 스켈레톤에서는 항상 PENDING으로 생성되고 audioUrl은 항상 null이다 — 엔진이 결정되면 합성
 * 로직이 이 상태를 COMPLETED로 전이시킨다.
 */
public class TtsRequest {

  private final UUID id;
  private final UUID confirmationId;
  private final UUID idempotencyKey;
  private final TtsRequestStatus status;
  private final String audioUrl;
  private final LocalDateTime createdAt;

  private TtsRequest(
      UUID id,
      UUID confirmationId,
      UUID idempotencyKey,
      TtsRequestStatus status,
      String audioUrl,
      LocalDateTime createdAt) {
    this.id = id;
    this.confirmationId = confirmationId;
    this.idempotencyKey = idempotencyKey;
    this.status = status;
    this.audioUrl = audioUrl;
    this.createdAt = createdAt;
  }

  public static TtsRequest create(UUID confirmationId, UUID idempotencyKey) {
    if (confirmationId == null) {
      throw new IllegalArgumentException("TTS 요청은 confirmation에 반드시 속해야 합니다.");
    }
    if (idempotencyKey == null) {
      throw new IllegalArgumentException("idempotencyKey는 필수입니다.");
    }
    return new TtsRequest(
        UUID.randomUUID(),
        confirmationId,
        idempotencyKey,
        TtsRequestStatus.PENDING,
        null,
        LocalDateTime.now());
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
  public static TtsRequest reconstitute(
      UUID id,
      UUID confirmationId,
      UUID idempotencyKey,
      TtsRequestStatus status,
      String audioUrl,
      LocalDateTime createdAt) {
    return new TtsRequest(id, confirmationId, idempotencyKey, status, audioUrl, createdAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getConfirmationId() {
    return confirmationId;
  }

  public UUID getIdempotencyKey() {
    return idempotencyKey;
  }

  public TtsRequestStatus getStatus() {
    return status;
  }

  public String getAudioUrl() {
    return audioUrl;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
