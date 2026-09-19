package com.voicebridge.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

/** 개인화 학습 작업 상태 조회. API 명세서 4.3절 참고. */
public interface GetPersonalizationTrainingStatusUseCase {

  TrainingStatusResult getStatus(UUID userId, UUID jobId);

  record TrainingStatusResult(
      UUID jobId,
      String status,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      String failureReason) {
    // TODO(백엔드 B): progress(진행률) 필드는 학습 파이프라인 쪽 값 전달 방식이 정해지면 추가
  }
}
