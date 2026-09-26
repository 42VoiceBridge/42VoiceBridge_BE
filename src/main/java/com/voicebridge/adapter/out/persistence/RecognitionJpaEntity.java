package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.recognition.ModelType;
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

// (user_id, created_at, id) 복합 인덱스는 findByUserId의 사용자별 조회 + createdAt desc, id desc 정렬 패턴 때문에 필요하다.
@Entity
@Table(
    name = "recognitions",
    indexes =
        @Index(
            name = "idx_recognitions_user_id_created_at_id",
            columnList = "user_id, created_at DESC, id DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecognitionJpaEntity {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String recognizedText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModelType modelUsed;

  @Column(nullable = false)
  private double confidence;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private RecognitionJpaEntity(
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
}
