package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.recognition.ModelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* 인식 결과를 DB에 어떻게 저장할건지 저장 형태 정의
 * PK : Id
 * */

@Entity
@Table(name = "recognitions")
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
