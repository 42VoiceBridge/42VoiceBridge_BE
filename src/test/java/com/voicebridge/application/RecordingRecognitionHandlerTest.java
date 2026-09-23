package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.RecordingStatus;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.RecordingRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingRecognitionHandlerTest {

  @Mock private RecordingRepositoryPort recordingRepositoryPort;
  @Mock private AiInferenceClient aiInferenceClient;

  private RecordingRecognitionHandler handler;

  private final UUID userId = UUID.randomUUID();
  private final byte[] audioBytes = new byte[] {1, 2, 3};

  @BeforeEach
  void setUp() {
    handler = new RecordingRecognitionHandler(recordingRepositoryPort, aiInferenceClient);
  }

  /** 업로드 서비스가 이미 PROCESSING으로 저장한 뒤 이벤트를 발행하므로, 핸들러가 보는 상태는 항상 PROCESSING이다. */
  private Recording processingRecording() {
    Recording recording =
        Recording.create(UUID.randomUUID(), UUID.randomUUID(), userId, "recordings/a.wav");
    recording.markProcessing();
    return recording;
  }

  private Recording captureSaved() {
    ArgumentCaptor<Recording> captor = ArgumentCaptor.forClass(Recording.class);
    verify(recordingRepositoryPort).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void 인식에_성공하면_결과를_반영해_DONE으로_저장한다() {
    Recording recording = processingRecording();
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    when(aiInferenceClient.recognize(any(), any(), any()))
        .thenReturn(new AiInferenceClient.RecognitionResult("오늘 날씨가 좋습니다.", 0.87));

    handler.handle(new RecordingUploadedEvent(recording.getId(), audioBytes));

    Recording saved = captureSaved();
    assertThat(saved.getStatus()).isEqualTo(RecordingStatus.DONE);
    assertThat(saved.getRecognizedText()).isEqualTo("오늘 날씨가 좋습니다.");
    assertThat(saved.getConfidence()).isEqualTo(0.87);
  }

  @Test
  void 무음으로_인식되면_빈_텍스트로_DONE_처리한다() {
    Recording recording = processingRecording();
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    when(aiInferenceClient.recognize(any(), any(), any()))
        .thenReturn(new AiInferenceClient.RecognitionResult("", 0.0));

    handler.handle(new RecordingUploadedEvent(recording.getId(), audioBytes));

    Recording saved = captureSaved();
    assertThat(saved.getStatus()).isEqualTo(RecordingStatus.DONE);
    assertThat(saved.getRecognizedText()).isEmpty();
  }

  @Test
  void AI_호출이_실패하면_FAILED로_저장한다() {
    Recording recording = processingRecording();
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    when(aiInferenceClient.recognize(any(), any(), any()))
        .thenThrow(new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE));

    handler.handle(new RecordingUploadedEvent(recording.getId(), audioBytes));

    Recording saved = captureSaved();
    assertThat(saved.getStatus()).isEqualTo(RecordingStatus.FAILED);
    assertThat(saved.getRecognizedText()).isNull();
  }

  /** 예외를 밖으로 던지면 비동기 스레드에서 사라져 녹음이 PROCESSING에 영구히 갇힌다. */
  @Test
  void AI_호출이_실패해도_예외를_밖으로_던지지_않는다() {
    Recording recording = processingRecording();
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    when(aiInferenceClient.recognize(any(), any(), any()))
        .thenThrow(new RuntimeException("네이티브 브릿지 크래시"));

    handler.handle(new RecordingUploadedEvent(recording.getId(), audioBytes));

    assertThat(captureSaved().getStatus()).isEqualTo(RecordingStatus.FAILED);
  }

  @Test
  void 녹음을_찾지_못하면_아무것도_저장하지_않는다() {
    UUID unknownId = UUID.randomUUID();
    when(recordingRepositoryPort.findById(unknownId)).thenReturn(Optional.empty());

    handler.handle(new RecordingUploadedEvent(unknownId, audioBytes));

    verify(recordingRepositoryPort, never()).save(any());
    verify(aiInferenceClient, never()).recognize(any(), any(), any());
  }

  @Test
  void 진단_세션은_개인화_이전이라_BASE_ADAPTED_모델로_호출한다() {
    Recording recording = processingRecording();
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    when(aiInferenceClient.recognize(any(), any(), any()))
        .thenReturn(new AiInferenceClient.RecognitionResult("오늘 날씨가 좋습니다.", 0.87));

    handler.handle(new RecordingUploadedEvent(recording.getId(), audioBytes));

    verify(aiInferenceClient).recognize(eq(audioBytes), eq(ModelType.BASE_ADAPTED), eq(userId));
  }
}
