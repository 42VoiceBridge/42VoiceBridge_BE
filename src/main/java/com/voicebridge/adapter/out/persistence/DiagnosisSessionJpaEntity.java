package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "diagnosis_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiagnosisSessionJpaEntity {

  @Id private UUID id;

  private UUID userId;

  @Enumerated(EnumType.STRING)
  private DiagnosisSessionStatus status;

  @ElementCollection
  @CollectionTable(
      name = "diagnosis_session_sentences",
      joinColumns = @JoinColumn(name = "session_id"))
  @OrderColumn(name = "sentence_order")
  private List<UUID> sentenceIds;

  private LocalDateTime createdAt;

  @Builder
  private DiagnosisSessionJpaEntity(
      UUID id,
      UUID userId,
      DiagnosisSessionStatus status,
      List<UUID> sentenceIds,
      LocalDateTime createdAt) {
    this.id = id;
    this.userId = userId;
    this.status = status;
    this.sentenceIds = sentenceIds;
    this.createdAt = createdAt;
  }
}
