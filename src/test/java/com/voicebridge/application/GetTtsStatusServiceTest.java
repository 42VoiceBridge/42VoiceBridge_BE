package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.Confirmation;
import com.voicebridge.domain.recognition.TtsRequest;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import com.voicebridge.port.out.TtsRequestRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTtsStatusServiceTest {
  @Mock TtsRequestRepositoryPort ttsRequestRepositoryPort;
  @Mock ConfirmationRepositoryPort confirmationRepositoryPort;
  GetTtsStatusService service;

  final UUID userId = UUID.randomUUID();
  final UUID confirmationId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new GetTtsStatusService(ttsRequestRepositoryPort, confirmationRepositoryPort);
  }

  @Test
  void 본인_TTS_요청이면_상태를_반환한다() {
    TtsRequest ttsRequest = TtsRequest.create(confirmationId, UUID.randomUUID());
    Confirmation confirmation = Confirmation.create(UUID.randomUUID(), userId, "물 좀 주세요");
    when(ttsRequestRepositoryPort.findById(ttsRequest.getId())).thenReturn(Optional.of(ttsRequest));
    when(confirmationRepositoryPort.findById(confirmationId)).thenReturn(Optional.of(confirmation));

    var result = service.getStatus(userId, ttsRequest.getId());

    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.audioUrl()).isNull();
  }

  @Test
  void 존재하지_않는_ttsId면_예외() {
    UUID ttsId = UUID.randomUUID();
    when(ttsRequestRepositoryPort.findById(ttsId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getStatus(userId, ttsId))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void 타인의_TTS_요청이면_예외() {
    TtsRequest ttsRequest = TtsRequest.create(confirmationId, UUID.randomUUID());
    Confirmation othersConfirmation =
        Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), "물 좀 주세요");
    when(ttsRequestRepositoryPort.findById(ttsRequest.getId())).thenReturn(Optional.of(ttsRequest));
    when(confirmationRepositoryPort.findById(confirmationId))
        .thenReturn(Optional.of(othersConfirmation));

    assertThatThrownBy(() -> service.getStatus(userId, ttsRequest.getId()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN_ACCESS);
  }
}
