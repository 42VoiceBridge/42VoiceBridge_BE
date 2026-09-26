package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class RequestTtsServiceTest {
  @Mock ConfirmationRepositoryPort confirmationRepositoryPort;
  @Mock TtsRequestRepositoryPort ttsRequestRepositoryPort;
  RequestTtsService service;

  final UUID userId = UUID.randomUUID();
  final UUID confirmationId = UUID.randomUUID();
  final UUID idempotencyKey = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new RequestTtsService(confirmationRepositoryPort, ttsRequestRepositoryPort);
  }

  private Confirmation validConfirmation() {
    Confirmation confirmation = Confirmation.create(UUID.randomUUID(), userId, "물 좀 주세요");
    when(confirmationRepositoryPort.findById(confirmationId)).thenReturn(Optional.of(confirmation));
    return confirmation;
  }

  @Test
  void 정상_요청이면_새_TtsRequest를_PENDING으로_저장한다() {
    validConfirmation();
    when(ttsRequestRepositoryPort.findByIdempotencyKey(idempotencyKey))
        .thenReturn(Optional.empty());
    when(ttsRequestRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

    var result = service.request(userId, confirmationId, idempotencyKey);

    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.ttsId()).isNotNull();
  }

  @Test
  void 존재하지_않는_confirmation이면_예외() {
    when(confirmationRepositoryPort.findById(confirmationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.request(userId, confirmationId, idempotencyKey))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void 타인의_confirmation이면_예외() {
    Confirmation othersConfirmation =
        Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), "물 좀 주세요");
    when(confirmationRepositoryPort.findById(confirmationId))
        .thenReturn(Optional.of(othersConfirmation));

    assertThatThrownBy(() -> service.request(userId, confirmationId, idempotencyKey))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN_ACCESS);
  }

  @Test
  void 무효화된_confirmation으로_요청하면_409에_해당하는_IllegalStateException() {
    Confirmation confirmation = Confirmation.create(UUID.randomUUID(), userId, "물 좀 주세요");
    confirmation.invalidate();
    when(confirmationRepositoryPort.findById(confirmationId)).thenReturn(Optional.of(confirmation));

    assertThatThrownBy(() -> service.request(userId, confirmationId, idempotencyKey))
        .isInstanceOf(IllegalStateException.class);
    verify(ttsRequestRepositoryPort, never()).save(any());
  }

  @Test
  void 같은_idempotencyKey로_재요청하면_기존_결과를_그대로_반환한다() {
    validConfirmation();
    TtsRequest existing = TtsRequest.create(confirmationId, idempotencyKey);
    when(ttsRequestRepositoryPort.findByIdempotencyKey(idempotencyKey))
        .thenReturn(Optional.of(existing));

    var result = service.request(userId, confirmationId, idempotencyKey);

    assertThat(result.ttsId()).isEqualTo(existing.getId());
    assertThat(result.status()).isEqualTo("PENDING");
    verify(ttsRequestRepositoryPort, never()).save(any());
  }

  @Test
  void 다른_confirmation에_이미_쓴_키를_재사용하면_400에_해당하는_IllegalArgumentException() {
    validConfirmation();
    TtsRequest existingForOtherConfirmation = TtsRequest.create(UUID.randomUUID(), idempotencyKey);
    when(ttsRequestRepositoryPort.findByIdempotencyKey(idempotencyKey))
        .thenReturn(Optional.of(existingForOtherConfirmation));

    assertThatThrownBy(() -> service.request(userId, confirmationId, idempotencyKey))
        .isInstanceOf(IllegalArgumentException.class);
    verify(ttsRequestRepositoryPort, never()).save(any());
  }
}
