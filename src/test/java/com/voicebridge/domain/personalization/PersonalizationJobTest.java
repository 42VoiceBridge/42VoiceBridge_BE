package com.voicebridge.domain.personalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
    assertThatThrownBy(job::markInProgress)
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
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

  @Test
  void 최소_다섯_개로_생성할_수_있다() {
    var job = PersonalizationJob.create(UUID.randomUUID(), 5);
    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.PENDING);
    assertThat(job.getCompletedAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 4})
  void 녹음이_부족하면_생성할_수_없다(int count) {
    assertThatThrownBy(() -> PersonalizationJob.create(UUID.randomUUID(), count))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_RECORDINGS);
  }

  @Test
  void 사용자_없이_생성할_수_없다() {
    assertThatThrownBy(() -> PersonalizationJob.create(null, 5))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @ParameterizedTest
  @EnumSource(
      value = PersonalizationJobStatus.class,
      names = {"PENDING", "IN_PROGRESS"})
  void 대기_또는_진행중_작업은_실패할_수_있다(PersonalizationJobStatus status) {
    var job = PersonalizationJob.create(UUID.randomUUID(), 5);
    if (status == PersonalizationJobStatus.IN_PROGRESS) job.markInProgress();
    job.fail("학습 요청 실패");
    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.FAILED);
    assertThat(job.getFailureReason()).isEqualTo("학습 요청 실패");
    assertThat(job.getCompletedAt()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = PersonalizationJobStatus.class,
      names = {"COMPLETED", "FAILED"})
  void 종료된_작업은_다시_변경할_수_없다(PersonalizationJobStatus status) {
    var job = PersonalizationJob.create(UUID.randomUUID(), 5);
    job.markInProgress();
    if (status == PersonalizationJobStatus.COMPLETED) job.complete("v1", "models/v1.pt");
    else job.fail("학습 실패");
    var completedAt = job.getCompletedAt();
    assertThatThrownBy(job::markInProgress)
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThatThrownBy(() -> job.complete("v2", "models/v2.pt"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThatThrownBy(() -> job.fail("다른 실패 사유"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThat(job.getStatus()).isEqualTo(status);
    assertThat(job.getCompletedAt()).isEqualTo(completedAt);
    assertThat(job.getModelVersion())
        .isEqualTo(status == PersonalizationJobStatus.COMPLETED ? "v1" : null);
    assertThat(job.getFailureReason())
        .isEqualTo(status == PersonalizationJobStatus.FAILED ? "학습 실패" : null);
  }

  @Test
  void 시작하지_않은_작업은_완료할_수_없다() {
    var job = PersonalizationJob.create(UUID.randomUUID(), 5);
    assertThatThrownBy(() -> job.complete("v1", "models/v1.pt"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.PENDING);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void 모델정보가_없으면_완료되지_않는다(String invalid) {
    var job = PersonalizationJob.create(UUID.randomUUID(), 5);
    job.markInProgress();
    assertThatThrownBy(() -> job.complete(invalid, "models/v1.pt"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThatThrownBy(() -> job.complete("v1", invalid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.IN_PROGRESS);
    assertThat(job.getModelVersion()).isNull();
    assertThat(job.getModelArtifactPath()).isNull();
    assertThat(job.getCompletedAt()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void 실패_사유가_없으면_상태를_변경하지_않는다(String reason) {
    var job = PersonalizationJob.create(UUID.randomUUID(), 5);
    assertThatThrownBy(() -> job.fail(reason))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(job.getStatus()).isEqualTo(PersonalizationJobStatus.PENDING);
    assertThat(job.getFailureReason()).isNull();
    assertThat(job.getCompletedAt()).isNull();
  }
}
