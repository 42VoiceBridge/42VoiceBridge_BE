package com.voicebridge.domain.personalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalizationRecordingEligibilityTest {
  private final LocalDateTime now = LocalDateTime.of(2026, 10, 2, 12, 0);

  @Test
  void 동의_업로드만으로는_학습_후보가_되지_않는다() {
    assertThat(recording("UPLOADED", true, null, null).isTrainingCandidate(now, 30)).isFalse();
  }

  @Test
  void 검토된_정답과_풀_버전이_있고_보관_기간_내일_때만_후보다() {
    assertThat(recording("UPLOADED", true, "pool-v1", "실제 읽은 문장").isTrainingCandidate(now, 30))
        .isTrue();
    assertThat(
            recording("DELETION_PENDING", true, "pool-v1", "실제 읽은 문장").isTrainingCandidate(now, 30))
        .isFalse();
    assertThat(recording("UPLOADED", false, "pool-v1", "실제 읽은 문장").isTrainingCandidate(now, 30))
        .isFalse();
    assertThat(
            recording("UPLOADED", true, "pool-v1", "실제 읽은 문장")
                .isTrainingCandidate(now.plusDays(31), 30))
        .isFalse();
  }

  private PersonalizationRecording recording(
      String status, boolean consent, String poolVersion, String spokenText) {
    return new PersonalizationRecording(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "p-1",
        "제안 문장",
        "personalization/key.wav",
        consent,
        "consent-v1",
        now,
        "wav",
        "pcm_s16le",
        16000,
        1,
        16000,
        "source-hash",
        "wav-hash",
        "normalize-v1",
        status,
        now,
        poolVersion,
        spokenText,
        spokenText == null ? null : "review-v1",
        spokenText == null ? null : UUID.randomUUID(),
        spokenText == null ? null : now);
  }
}
