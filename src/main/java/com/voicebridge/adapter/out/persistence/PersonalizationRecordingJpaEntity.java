package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "personalization_recordings",
    indexes = {
      @Index(name = "idx_personalization_recordings_user", columnList = "userId"),
      @Index(
          name = "idx_personalization_recordings_status_created",
          columnList = "status,createdAt")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalizationRecordingJpaEntity {
  @Id private UUID id;
  private UUID userId;
  private UUID shownPromptId;
  private String promptId;

  @Column(length = 500)
  private String promptText;

  private String storageKey;
  private boolean useForTraining;
  private String consentVersion;
  private LocalDateTime consentedAt;
  private String sourceFormat;
  private String sourceCodec;
  private int sourceSampleRate;
  private int sourceChannels;
  private int sampleCount;
  private String sourceSha256;
  private String wavSha256;
  private String normalizationVersion;
  private String status;
  private LocalDateTime createdAt;
  private String promptPoolVersion;

  @Column(length = 500)
  private String reviewedSpokenText;

  private String reviewRevision;
  private UUID reviewedBy;
  private LocalDateTime reviewedAt;

  public PersonalizationRecordingJpaEntity(
      com.voicebridge.domain.personalization.PersonalizationRecording r) {
    id = r.id();
    userId = r.userId();
    shownPromptId = r.shownPromptId();
    promptId = r.promptId();
    promptText = r.promptText();
    storageKey = r.storageKey();
    useForTraining = r.useForTraining();
    consentVersion = r.consentVersion();
    consentedAt = r.consentedAt();
    sourceFormat = r.sourceFormat();
    sourceCodec = r.sourceCodec();
    sourceSampleRate = r.sourceSampleRate();
    sourceChannels = r.sourceChannels();
    sampleCount = r.sampleCount();
    sourceSha256 = r.sourceSha256();
    wavSha256 = r.wavSha256();
    normalizationVersion = r.normalizationVersion();
    status = r.status().name();
    createdAt = r.createdAt();
    promptPoolVersion = r.promptPoolVersion();
    reviewedSpokenText = r.reviewedSpokenText();
    reviewRevision = r.reviewRevision();
    reviewedBy = r.reviewedBy();
    reviewedAt = r.reviewedAt();
  }

  public void setPersistenceStatus(String status) {
    this.status = status;
  }

  public com.voicebridge.domain.personalization.PersonalizationRecording toDomain() {
    return new com.voicebridge.domain.personalization.PersonalizationRecording(
        id,
        userId,
        shownPromptId,
        promptId,
        promptText,
        storageKey,
        useForTraining,
        consentVersion,
        consentedAt,
        sourceFormat,
        sourceCodec,
        sourceSampleRate,
        sourceChannels,
        sampleCount,
        sourceSha256,
        wavSha256,
        normalizationVersion,
        com.voicebridge.domain.personalization.PersonalizationRecordingStatus.valueOf(status),
        createdAt,
        promptPoolVersion,
        reviewedSpokenText,
        reviewRevision,
        reviewedBy,
        reviewedAt);
  }
}
