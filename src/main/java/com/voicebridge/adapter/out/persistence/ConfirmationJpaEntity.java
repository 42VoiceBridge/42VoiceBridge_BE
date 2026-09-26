package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// recognition_id 인덱스는 findValidByRecognitionId(인식 결과별 최신 유효 확인 조회) 패턴 때문에 필요하다.
@Entity
@Table(
    name = "confirmations",
    indexes = @Index(name = "idx_confirmations_recognition_id", columnList = "recognition_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmationJpaEntity {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID recognitionId;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String confirmedText;

  @Column(nullable = false)
  private boolean valid;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private ConfirmationJpaEntity(
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
}
