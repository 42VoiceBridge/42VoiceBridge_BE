package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.voicebridge.domain.personalization.PersonalizationAdapter;
import com.voicebridge.domain.personalization.PersonalizationAdapterStatus;
import com.voicebridge.port.out.PersonalizationAdapterRepositoryPort;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPersonalizationModelServiceTest {

  @Mock private PersonalizationAdapterRepositoryPort personalizationAdapterRepositoryPort;

  private GetPersonalizationModelService service;

  @BeforeEach
  void setUp() {
    service = new GetPersonalizationModelService(personalizationAdapterRepositoryPort);
  }

  @Test
  void 완료된_학습이_없으면_hasPersonalizedModel이_false다() {
    UUID userId = UUID.randomUUID();
    when(personalizationAdapterRepositoryPort.findActiveByUserId(userId))
        .thenReturn(Optional.empty());

    var result = service.getModelStatus(userId);

    assertThat(result.hasPersonalizedModel()).isFalse();
    assertThat(result.modelVersion()).isNull();
  }

  @Test
  void 활성_어댑터가_있으면_모델_정보를_반환한다() {
    UUID userId = UUID.randomUUID();
    LocalDateTime trainedAt = LocalDateTime.now();
    PersonalizationAdapter adapter =
        new PersonalizationAdapter(
            UUID.randomUUID(),
            userId,
            UUID.randomUUID(),
            PersonalizationAdapterStatus.ACTIVE,
            "v1",
            "base-revision",
            "sha256",
            8,
            trainedAt,
            trainedAt.plusMinutes(1));
    when(personalizationAdapterRepositoryPort.findActiveByUserId(userId))
        .thenReturn(Optional.of(adapter));

    var result = service.getModelStatus(userId);

    assertThat(result.hasPersonalizedModel()).isTrue();
    assertThat(result.modelVersion()).isEqualTo("v1");
    assertThat(result.trainingRecordingCount()).isEqualTo(8);
    assertThat(result.trainedAt()).isEqualTo(trainedAt);
  }
}
