package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.domain.recognition.Confirmation;
import com.voicebridge.domain.recognition.TtsRequest;
import com.voicebridge.domain.recognition.TtsRequestStatus;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TtsEnginePort;
import com.voicebridge.port.out.TtsRequestRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TtsSynthesisHandlerTest {

  @Mock private TtsRequestRepositoryPort ttsRequestRepositoryPort;
  @Mock private TtsEnginePort ttsEnginePort;
  @Mock private StoragePort storagePort;

  @Mock private ConfirmationRepositoryPort confirmationRepositoryPort;

  private TtsSynthesisHandler handler;

  private final String confirmedText = "물 좀 주세요";

  @BeforeEach
  void setUp() {
    handler =
        new TtsSynthesisHandler(
            ttsRequestRepositoryPort, ttsEnginePort, storagePort, confirmationRepositoryPort);
  }

  private TtsRequest pendingRequest() {
    var confirmation = Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), confirmedText);
    when(confirmationRepositoryPort.findById(confirmation.getId()))
        .thenReturn(Optional.of(confirmation));
    return TtsRequest.create(confirmation.getId(), UUID.randomUUID());
  }

  private TtsRequest captureSaved() {
    ArgumentCaptor<TtsRequest> captor = ArgumentCaptor.forClass(TtsRequest.class);
    verify(ttsRequestRepositoryPort).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void 합성에_성공하면_오디오를_저장하고_COMPLETED로_저장한다() {
    TtsRequest ttsRequest = pendingRequest();
    when(ttsRequestRepositoryPort.findById(ttsRequest.getId())).thenReturn(Optional.of(ttsRequest));
    byte[] audio = {1, 2, 3};
    when(ttsEnginePort.synthesize(confirmedText)).thenReturn(audio);
    when(storagePort.upload(audio, "tts.mp3")).thenReturn("tts/abc.mp3");

    handler.handle(new TtsRequestedEvent(ttsRequest.getId(), confirmedText));

    TtsRequest saved = captureSaved();
    assertThat(saved.getStatus()).isEqualTo(TtsRequestStatus.COMPLETED);
    assertThat(saved.getAudioStorageKey()).isEqualTo("tts/abc.mp3");
  }

  @Test
  void 합성이_실패하면_FAILED로_저장하고_예외를_밖으로_던지지_않는다() {
    TtsRequest ttsRequest = pendingRequest();
    when(ttsRequestRepositoryPort.findById(ttsRequest.getId())).thenReturn(Optional.of(ttsRequest));
    when(ttsEnginePort.synthesize(confirmedText)).thenThrow(new RuntimeException("CLOVA 오류"));

    handler.handle(new TtsRequestedEvent(ttsRequest.getId(), confirmedText));

    TtsRequest saved = captureSaved();
    assertThat(saved.getStatus()).isEqualTo(TtsRequestStatus.FAILED);
    assertThat(saved.getAudioStorageKey()).isNull();
  }

  @Test
  void 저장이_실패해도_FAILED로_저장한다() {
    TtsRequest ttsRequest = pendingRequest();
    when(ttsRequestRepositoryPort.findById(ttsRequest.getId())).thenReturn(Optional.of(ttsRequest));
    byte[] audio = {1, 2, 3};
    when(ttsEnginePort.synthesize(confirmedText)).thenReturn(audio);
    when(storagePort.upload(audio, "tts.mp3")).thenThrow(new RuntimeException("업로드 실패"));

    handler.handle(new TtsRequestedEvent(ttsRequest.getId(), confirmedText));

    TtsRequest saved = captureSaved();
    assertThat(saved.getStatus()).isEqualTo(TtsRequestStatus.FAILED);
  }

  @Test
  void 요청을_찾지_못하면_아무것도_하지_않는다() {
    UUID unknownId = UUID.randomUUID();
    when(ttsRequestRepositoryPort.findById(unknownId)).thenReturn(Optional.empty());

    handler.handle(new TtsRequestedEvent(unknownId, confirmedText));

    verify(ttsRequestRepositoryPort, never()).save(any());
    verify(ttsEnginePort, never()).synthesize(any());
  }

  @Test
  void 무효화된_확인은_합성과_업로드_없이_FAILED로_저장한다() {
    var confirmation = Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), confirmedText);
    confirmation.invalidate();
    var request = TtsRequest.create(confirmation.getId(), UUID.randomUUID());
    when(ttsRequestRepositoryPort.findById(request.getId())).thenReturn(Optional.of(request));
    when(confirmationRepositoryPort.findById(confirmation.getId()))
        .thenReturn(Optional.of(confirmation));

    handler.handle(new TtsRequestedEvent(request.getId(), confirmedText));

    assertThat(captureSaved().getStatus()).isEqualTo(TtsRequestStatus.FAILED);
    verify(ttsEnginePort, never()).synthesize(any());
    verify(storagePort, never()).upload(any(), any());
  }

  @Test
  void 확인이_없으면_합성과_업로드_없이_FAILED로_저장한다() {
    var request = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());
    when(ttsRequestRepositoryPort.findById(request.getId())).thenReturn(Optional.of(request));
    when(confirmationRepositoryPort.findById(request.getConfirmationId()))
        .thenReturn(Optional.empty());

    handler.handle(new TtsRequestedEvent(request.getId(), confirmedText));

    assertThat(captureSaved().getStatus()).isEqualTo(TtsRequestStatus.FAILED);
    verify(ttsEnginePort, never()).synthesize(any());
    verify(storagePort, never()).upload(any(), any());
  }
}
