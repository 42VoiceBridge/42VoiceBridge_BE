package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.port.in.GetPersonalizationTrainingStatusUseCase;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPersonalizationTrainingStatusService
    implements GetPersonalizationTrainingStatusUseCase {

  private final PersonalizationJobRepositoryPort personalizationJobRepositoryPort;

  @Override
  public TrainingStatusResult getStatus(UUID userId, UUID jobId) {
    PersonalizationJob job =
        personalizationJobRepositoryPort
            .findById(jobId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!job.getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }

    return new TrainingStatusResult(
        job.getId(),
        job.getStatus().name(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getFailureReason());
  }
}
