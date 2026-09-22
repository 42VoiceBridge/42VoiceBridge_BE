package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase.UploadCommand;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UploadDiagnosisRecordingServiceTest {

  @Mock private DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  @Mock private RecordingRepositoryPort recordingRepositoryPort;
  @Mock private StoragePort storagePort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private UploadDiagnosisRecordingService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID sentenceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new UploadDiagnosisRecordingService(
            diagnosisSessionRepositoryPort, recordingRepositoryPort, storagePort, eventPublisher);
  }

  private DiagnosisSession sessionOwnedBy(UUID ownerId) {
    return DiagnosisSession.reconstitute(
        sessionId,
        ownerId,
        DiagnosisSessionStatus.IN_PROGRESS,
        List.of(sentenceId),
        LocalDateTime.now());
  }

  private UploadCommand command() {
    return new UploadCommand(userId, sessionId, sentenceId, new byte[] {1, 2, 3}, "recording.wav");
  }

  @Test
  void 업로드하면_PROCESSING_상태로_저장하고_인식_이벤트를_발행한다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    when(storagePort.upload(any(), any())).thenReturn("recordings/abc.wav");
    when(recordingRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

    var result = service.upload(command());

    assertThat(result.status()).isEqualTo("PROCESSING");
    assertThat(result.recordingId()).isNotNull();

    ArgumentCaptor<RecordingUploadedEvent> event =
        ArgumentCaptor.forClass(RecordingUploadedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().recordingId()).isEqualTo(result.recordingId());
  }

  @Test
  void 세션이_없으면_예외를_던지고_파일을_올리지_않는다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.upload(command()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

    verify(storagePort, never()).upload(any(), any());
  }

  @Test
  void 남의_세션에는_업로드할_수_없다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(UUID.randomUUID())));

    assertThatThrownBy(() -> service.upload(command()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_ACCESS);

    verify(storagePort, never()).upload(any(), any());
    verify(eventPublisher, never()).publishEvent(any(RecordingUploadedEvent.class));
  }

  @Test
  void 세션에_속하지_않은_문장은_업로드할_수_없다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    UploadCommand otherSentence =
        new UploadCommand(userId, sessionId, UUID.randomUUID(), new byte[] {1}, "recording.wav");

    assertThatThrownBy(() -> service.upload(otherSentence))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

    verify(storagePort, never()).upload(any(), any());
  }
}
