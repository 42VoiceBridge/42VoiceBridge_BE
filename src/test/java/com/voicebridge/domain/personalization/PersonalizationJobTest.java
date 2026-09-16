package com.voicebridge.domain.personalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalizationJobTest {

  @Test
  void 생성하면_PENDING_상태다() {
    PersonalizationJob job = PersonalizationJob.create(UUID.randomUUID(), 8);

    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.PENDING);
    assertThat(job.getTrainingRecordingCount()).isEqualTo(8);
  }

  @Test
  void PENDING_상태에서만_IN_PROGRESS로_전이할_수_있다() {
    PersonalizationJob job = PersonalizationJob.create(UUID.randomUUID(), 8);

    job.markInProgress();

    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.IN_PROGRESS);
    assertThatThrownBy(job::markInProgress).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void IN_PROGRESS_상태에서_완료하면_모델정보가_채워진다() {
    PersonalizationJob job = PersonalizationJob.create(UUID.randomUUID(), 8);
    job.markInProgress();

    job.complete("v1", "s3://models/v1.pt");

    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.COMPLETED);
    assertThat(job.getModelVersion()).isEqualTo("v1");
    assertThat(job.getCompletedAt()).isNotNull();
  }
}
