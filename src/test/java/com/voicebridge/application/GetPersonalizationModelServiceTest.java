package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPersonalizationModelServiceTest {

  @Mock private PersonalizationJobRepositoryPort personalizationJobRepositoryPort;

  private GetPersonalizationModelService service;

  @BeforeEach
  void setUp() {
    service = new GetPersonalizationModelService(personalizationJobRepositoryPort);
  }

  @Test
  void 완료된_학습이_없으면_hasPersonalizedModel이_false다() {
    UUID userId = UUID.randomUUID();
    when(personalizationJobRepositoryPort.findLatestCompletedByUserId(userId))
        .thenReturn(Optional.empty());

    var result = service.getModelStatus(userId);

    assertThat(result.hasPersonalizedModel()).isFalse();
    assertThat(result.modelVersion()).isNull();
  }

  @Test
  void 완료된_학습이_있으면_모델_정보를_반환한다() {
    UUID userId = UUID.randomUUID();
    PersonalizationJob job = PersonalizationJob.create(userId, 8);
    job.markInProgress();
    job.complete("v1", "s3://models/v1.pt");
    when(personalizationJobRepositoryPort.findLatestCompletedByUserId(userId))
        .thenReturn(Optional.of(job));

    var result = service.getModelStatus(userId);

    assertThat(result.hasPersonalizedModel()).isTrue();
    assertThat(result.modelVersion()).isEqualTo("v1");
    assertThat(result.trainingRecordingCount()).isEqualTo(8);
  }
}
