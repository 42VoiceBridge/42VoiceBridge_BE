package com.voicebridge.domain.recognition;

import java.time.LocalDateTime;
import java.util.UUID;

/** TTS(텍스트→음성) 요청. 생성 시 항상 PENDING이고, 비동기 합성 결과에 따라 COMPLETED 또는 FAILED로 전이한다. */
public class TtsRequest {

  private final UUID id;
  private final UUID confirmationId;
  private final UUID idempotencyKey;
  private TtsRequestStatus status;
  private String audioUrl;
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

  /** 합성이 끝나 저장된 오디오 경로를 반영한다. 비동기 합성이라 실패를 HTTP 응답으로 알릴 수 없어 상태로 남긴다. */
  public void markCompleted(String audioUrl) {
    if (status != TtsRequestStatus.PENDING) {
      throw new IllegalStateException("PENDING 상태인 요청만 완료 처리할 수 있습니다.");
    }
    if (audioUrl == null || audioUrl.isBlank()) {
      throw new IllegalArgumentException("완료 처리에는 오디오 경로가 필요합니다.");
    }
    this.audioUrl = audioUrl;
    this.status = TtsRequestStatus.COMPLETED;
  }

  public void markFailed() {
    if (status != TtsRequestStatus.PENDING) {
      throw new IllegalStateException("PENDING 상태인 요청만 실패 처리할 수 있습니다.");
    }
    this.status = TtsRequestStatus.FAILED;
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
