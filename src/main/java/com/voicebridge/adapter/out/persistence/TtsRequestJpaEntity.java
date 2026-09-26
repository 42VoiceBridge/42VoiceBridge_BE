package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.recognition.TtsRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// idempotency_key는 유니크 인덱스로 걸어 중복 방지를 DB 레벨에서도 보장한다 — 애플리케이션의 조회-후-생성 로직만 믿지 않는다.
@Entity
@Table(
    name = "tts_requests",
    indexes =
        @Index(
            name = "idx_tts_requests_idempotency_key",
            columnList = "idempotency_key",
            unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TtsRequestJpaEntity {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID confirmationId;

  @Column(nullable = false)
  private UUID idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TtsRequestStatus status;

  @Column(nullable = true)
  private String audioUrl;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private TtsRequestJpaEntity(
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
}
