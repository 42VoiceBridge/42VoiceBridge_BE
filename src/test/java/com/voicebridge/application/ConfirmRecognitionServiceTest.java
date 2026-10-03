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
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.domain.recognition.Recognition;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfirmRecognitionServiceTest {
  @Mock RecognitionRepositoryPort recognitionRepositoryPort;
  @Mock ConfirmationRepositoryPort confirmationRepositoryPort;
  ConfirmRecognitionService service;

  final UUID userId = UUID.randomUUID();
  final UUID recognitionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ConfirmRecognitionService(recognitionRepositoryPort, confirmationRepositoryPort);
  }

  private Recognition ownedRecognition() {
    Recognition recognition = Recognition.create(userId, "물 좀 주세요", ModelType.BASE_ADAPTED, 0.9);
    when(recognitionRepositoryPort.findById(recognitionId)).thenReturn(Optional.of(recognition));
    return recognition;
  }

  @Test
  void 정상적으로_확인하면_저장하고_결과를_반환한다() {
    ownedRecognition();
    when(confirmationRepositoryPort.findValidByRecognitionId(recognitionId))
        .thenReturn(Optional.empty());
    when(confirmationRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

    var result = service.confirm(userId, recognitionId, "물 좀 주세요");

    assertThat(result.confirmedText()).isEqualTo("물 좀 주세요");
    assertThat(result.confirmationId()).isNotNull();
    assertThat(result.confirmedAt()).isNotNull();
  }

  @Test
  void 재확인하면_이전_확인을_무효화하고_새로_저장한다() {
    ownedRecognition();
    Confirmation previous = Confirmation.create(recognitionId, userId, "이전 확인");
    when(confirmationRepositoryPort.findValidByRecognitionId(recognitionId))
        .thenReturn(Optional.of(previous));
    when(confirmationRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

    service.confirm(userId, recognitionId, "새 확인");

    assertThat(previous.isValid()).isFalse();
    verify(confirmationRepositoryPort).save(previous);
  }

  @Test
  void 존재하지_않는_recognition이면_예외() {
    when(recognitionRepositoryPort.findById(recognitionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirm(userId, recognitionId, "물 좀 주세요"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    verify(confirmationRepositoryPort, never()).save(any());
  }

  @Test
  void 타인의_recognition이면_예외() {
    Recognition othersRecognition =
        Recognition.create(UUID.randomUUID(), "물 좀 주세요", ModelType.BASE_ADAPTED, 0.9);
    when(recognitionRepositoryPort.findById(recognitionId))
        .thenReturn(Optional.of(othersRecognition));

    assertThatThrownBy(() -> service.confirm(userId, recognitionId, "물 좀 주세요"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN_ACCESS);
    verify(confirmationRepositoryPort, never()).save(any());
  }

  @Test
  void 빈_텍스트면_예외이고_기존_확인_조회조차_하지_않는다() {
    ownedRecognition();

    assertThatThrownBy(() -> service.confirm(userId, recognitionId, "   "))
        .isInstanceOf(IllegalArgumentException.class);

    verify(confirmationRepositoryPort, never()).findValidByRecognitionId(any());
    verify(confirmationRepositoryPort, never()).save(any());
  }
}
