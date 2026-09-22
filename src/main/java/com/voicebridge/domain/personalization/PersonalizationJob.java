package com.voicebridge.domain.personalization;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 개인화 학습 작업 도메인 엔티티. 상태 전이(PENDING → IN_PROGRESS → COMPLETED/FAILED)를 이 클래스가 직접 소유한다(Rich Domain
 * Model). "내 개인화 모델 상태"는 별도 엔티티 없이 "이 유저의 최신 COMPLETED job"으로부터 파생한다(YAGNI).
 */
public class PersonalizationJob {

  // 팀 확정 전 임시 정책. 최소 녹음 개수는 도메인에서만 관리한다.
  public static final int MIN_TRAINING_RECORDING_COUNT = 5;

  private final UUID id;
  private final UUID userId;
  private PersonalizationJobStatus status;
  private final int trainingRecordingCount;
  private String modelVersion;
  private String modelArtifactPath;
  private String failureReason;
  private final LocalDateTime startedAt;
  private LocalDateTime completedAt;

  private PersonalizationJob(
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

  public static PersonalizationJob create(UUID userId, int trainingRecordingCount) {
    if (userId == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    if (trainingRecordingCount < MIN_TRAINING_RECORDING_COUNT) {
      throw new CustomException(ErrorCode.INSUFFICIENT_RECORDINGS);
    }
    return new PersonalizationJob(
        UUID.randomUUID(),
        userId,
        PersonalizationJobStatus.PENDING,
        trainingRecordingCount,
        null,
        null,
        null,
        LocalDateTime.now(),
        null);
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
  public static PersonalizationJob reconstitute(
      UUID id,
      UUID userId,
      PersonalizationJobStatus status,
      int trainingRecordingCount,
      String modelVersion,
      String modelArtifactPath,
      String failureReason,
      LocalDateTime startedAt,
      LocalDateTime completedAt) {
    return new PersonalizationJob(
        id,
        userId,
        status,
        trainingRecordingCount,
        modelVersion,
        modelArtifactPath,
        failureReason,
        startedAt,
        completedAt);
  }

  public void markInProgress() {
    if (status != PersonalizationJobStatus.PENDING) {
      throw new CustomException(ErrorCode.INVALID_STATE_TRANSITION);
    }
    this.status = PersonalizationJobStatus.IN_PROGRESS;
  }

  public void complete(String modelVersion, String modelArtifactPath) {
    if (status != PersonalizationJobStatus.IN_PROGRESS) {
      throw new CustomException(ErrorCode.INVALID_STATE_TRANSITION);
    }
    if (modelVersion == null
        || modelVersion.isBlank()
        || modelArtifactPath == null
        || modelArtifactPath.isBlank()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    this.status = PersonalizationJobStatus.COMPLETED;
    this.modelVersion = modelVersion;
    this.modelArtifactPath = modelArtifactPath;
    this.completedAt = LocalDateTime.now();
  }

  public void fail(String reason) {
    if (status != PersonalizationJobStatus.PENDING
        && status != PersonalizationJobStatus.IN_PROGRESS) {
      throw new CustomException(ErrorCode.INVALID_STATE_TRANSITION);
    }
    if (reason == null || reason.isBlank()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    this.status = PersonalizationJobStatus.FAILED;
    this.failureReason = reason;
    this.completedAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public PersonalizationJobStatus getStatus() {
    return status;
  }

  public int getTrainingRecordingCount() {
    return trainingRecordingCount;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public String getModelArtifactPath() {
    return modelArtifactPath;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }
}
