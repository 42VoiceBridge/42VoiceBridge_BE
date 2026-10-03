package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.personalization.PersonalizationAdapter;
import com.voicebridge.domain.personalization.PersonalizationAdapterStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "personalization_adapters",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_personalization_active_user", columnNames = "active_user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalizationAdapterJpaEntity {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private UUID jobId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PersonalizationAdapterStatus status;

  @Column(name = "active_user_id")
  private UUID activeUserId;

  private String modelVersion;
  private String baseRevision;
  private String artifactSha256;
  private int trainingRecordingCount;
  private LocalDateTime trainedAt;
  private LocalDateTime activatedAt;

  public PersonalizationAdapterJpaEntity(PersonalizationAdapter adapter) {
    id = adapter.id();
    userId = adapter.userId();
    jobId = adapter.jobId();
    status = adapter.status();
    activeUserId = status == PersonalizationAdapterStatus.ACTIVE ? userId : null;
    modelVersion = adapter.modelVersion();
    baseRevision = adapter.baseRevision();
    artifactSha256 = adapter.artifactSha256();
    trainingRecordingCount = adapter.trainingRecordingCount();
    trainedAt = adapter.trainedAt();
    activatedAt = adapter.activatedAt();
  }

  public PersonalizationAdapter toDomain() {
    return new PersonalizationAdapter(
        id,
        userId,
        jobId,
        status,
        modelVersion,
        baseRevision,
        artifactSha256,
        trainingRecordingCount,
        trainedAt,
        activatedAt);
  }
}
