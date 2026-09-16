package com.voicebridge.application;

import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.port.in.GetPersonalizationModelUseCase;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPersonalizationModelService implements GetPersonalizationModelUseCase {

  private final PersonalizationJobRepositoryPort personalizationJobRepositoryPort;

  @Override
  public ModelStatusResult getModelStatus(UUID userId) {
    Optional<PersonalizationJob> latestCompleted =
        personalizationJobRepositoryPort.findLatestCompletedByUserId(userId);

    if (latestCompleted.isEmpty()) {
      return new ModelStatusResult(false, null, null, null);
    }

    PersonalizationJob job = latestCompleted.get();
    return new ModelStatusResult(
        true, job.getModelVersion(), job.getCompletedAt(), job.getTrainingRecordingCount());
  }
}
