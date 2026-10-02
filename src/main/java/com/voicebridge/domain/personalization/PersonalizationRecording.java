package com.voicebridge.domain.personalization;

import com.voicebridge.port.out.AudioNormalizationPort.Metadata;
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
    String status,
    LocalDateTime createdAt) {
  public static PersonalizationRecording prepare(
      UUID userId,
      UUID shownPromptId,
      String promptId,
      String promptText,
      boolean useForTraining,
      String consentVersion,
      Metadata metadata) {
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
        "PREPARING",
        now);
  }
}
