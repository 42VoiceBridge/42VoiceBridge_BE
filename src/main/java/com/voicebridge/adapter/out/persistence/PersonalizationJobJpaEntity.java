package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.personalization.PersonalizationJobStatus;
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

@Entity
@Table(name = "personalization_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalizationJobJpaEntity {

  @Id private UUID id;

  private UUID userId;

  @Enumerated(EnumType.STRING)
  private PersonalizationJobStatus status;

  private int trainingRecordingCount;

  private String modelVersion;

  private String modelArtifactPath;

  private String failureReason;

  private LocalDateTime startedAt;

  private LocalDateTime completedAt;

  @Builder
  private PersonalizationJobJpaEntity(
      UUID id,
      UUID userId,
      PersonalizationJobStatus status,
      int trainingRecordingCount,
      String modelVersion,
      String modelArtifactPath,
      String failureReason,
      LocalDateTime startedAt,
      LocalDateTime completedAt) {
    this.id = id;
    this.userId = userId;
    this.status = status;
    this.trainingRecordingCount = trainingRecordingCount;
    this.modelVersion = modelVersion;
    this.modelArtifactPath = modelArtifactPath;
    this.failureReason = failureReason;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
  }
}
