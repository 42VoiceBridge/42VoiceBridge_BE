package com.voicebridge.domain.personalization;

import java.time.LocalDateTime;
import java.util.UUID;

public record PersonalizationRecording(
    UUID id,
    UUID userId,
    UUID shownPromptId,
    String promptId,
    String promptText,
    String storageKey,
    boolean useForTraining,
    String consentVersion,
    LocalDateTime consentedAt,
    String sourceFormat,
    String sourceCodec,
    int sourceSampleRate,
    int sourceChannels,
    int sampleCount,
    String sourceSha256,
    String wavSha256,
    String normalizationVersion,
    PersonalizationRecordingStatus status,
    LocalDateTime createdAt,
    String promptPoolVersion,
    String reviewedSpokenText,
    String reviewRevision,
    UUID reviewedBy,
    LocalDateTime reviewedAt) {
  public static PersonalizationRecording prepare(
      UUID userId,
      UUID shownPromptId,
      String promptId,
      String promptText,
      boolean useForTraining,
      String consentVersion,
      AudioProvenance metadata) {
    UUID id = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();
    return new PersonalizationRecording(
        id,
        userId,
        shownPromptId,
        promptId,
        promptText,
        "personalization/" + id + ".wav",
        useForTraining,
        consentVersion,
        now,
        metadata.sourceFormat(),
        metadata.sourceCodec(),
        metadata.sourceSampleRate(),
        metadata.sourceChannels(),
        metadata.sampleCount(),
        metadata.sourceSha256(),
        metadata.wavSha256(),
        metadata.normalizationVersion(),
        PersonalizationRecordingStatus.PREPARING,
        now,
        null,
        null,
        null,
        null,
        null);
  }

  /** 후보 판정만 수행한다. 이 결과만으로 job 제출이나 train/dev 분할을 확정하지 않는다. */
  public boolean isTrainingCandidate(LocalDateTime now, int retentionDays) {
    return useForTraining
        && status == PersonalizationRecordingStatus.UPLOADED
        && createdAt != null
        && createdAt.plusDays(retentionDays).isAfter(now)
        && consentedAt != null
        && hasText(consentVersion)
        && hasText(promptText)
        && hasText(promptPoolVersion)
        && hasText(reviewedSpokenText)
        && hasText(reviewRevision)
        && reviewedBy != null
        && reviewedAt != null
        && hasText(wavSha256)
        && hasText(normalizationVersion)
        && hasText(storageKey);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
