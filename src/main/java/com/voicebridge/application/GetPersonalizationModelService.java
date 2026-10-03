package com.voicebridge.application;

import com.voicebridge.domain.personalization.PersonalizationAdapter;
import com.voicebridge.port.in.GetPersonalizationModelUseCase;
import com.voicebridge.port.out.PersonalizationAdapterRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPersonalizationModelService implements GetPersonalizationModelUseCase {

  private final PersonalizationAdapterRepositoryPort personalizationAdapterRepositoryPort;

  @Override
  public ModelStatusResult getModelStatus(UUID userId) {
    Optional<PersonalizationAdapter> active =
        personalizationAdapterRepositoryPort.findActiveByUserId(userId);

    if (active.isEmpty()) {
      return new ModelStatusResult(false, null, null, null);
    }

    PersonalizationAdapter adapter = active.get();
    return new ModelStatusResult(
        true, adapter.modelVersion(), adapter.trainedAt(), adapter.trainingRecordingCount());
  }
}
