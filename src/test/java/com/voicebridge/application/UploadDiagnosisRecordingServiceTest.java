package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase.UploadCommand;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.AudioNormalizationPort.Metadata;
import com.voicebridge.port.out.AudioNormalizationPort.NormalizedAudio;
import com.voicebridge.port.out.AudioProcessingException;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.InvalidAudioException;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UploadDiagnosisRecordingServiceTest {

  @Mock private DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  @Mock private RecordingRepositoryPort recordingRepositoryPort;
  @Mock private StoragePort storagePort;
  @Mock private AudioNormalizationPort audioNormalizationPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private UploadDiagnosisRecordingService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID sentenceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    // 트랜잭션은 여기서 보지 않는다(프록시 없이 직접 만든다). 잠금으로 줄 세우는 것은 DiagnosisRecordingRaceTest가 실제 DB로 확인한다.
    service =
        new UploadDiagnosisRecordingService(
            new DiagnosisRecordingRegistrar(
                diagnosisSessionRepositoryPort, recordingRepositoryPort, eventPublisher),
            audioNormalizationPort,
            storagePort);
  }

  private static final byte[] BROWSER_AUDIO = {1, 2, 3};
  private static final byte[] WAV = {9, 9, 9, 9};

  private void normalizerReturnsWav() {
    when(audioNormalizationPort.normalize(BROWSER_AUDIO))
        .thenReturn(
            new NormalizedAudio(
                WAV, new Metadata("matroska,webm", "opus", 48000, 1, 16000, "src", "wav", "v1")));
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
    return new UploadCommand(userId, sessionId, sentenceId, BROWSER_AUDIO, "recording.webm");
  }

  @Test
  void 업로드하면_변환한_WAV를_저장하고_PROCESSING으로_등록한_뒤_WAV로_인식을_요청한다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    normalizerReturnsWav();
    when(diagnosisSessionRepositoryPort.findByIdForUpdate(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    when(storagePort.upload(WAV, "recording.wav")).thenReturn("recordings/abc.wav");
    when(recordingRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

    var result = service.upload(command());

    assertThat(result.status()).isEqualTo("PROCESSING");
    assertThat(result.recordingId()).isNotNull();
    assertThat(result.sentenceId()).isEqualTo(sentenceId);

    ArgumentCaptor<RecordingUploadedEvent> event =
        ArgumentCaptor.forClass(RecordingUploadedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().recordingId()).isEqualTo(result.recordingId());
    // 브라우저 원본(WebM)이 아니라 변환된 WAV가 저장되고 AI로 간다
    verify(storagePort).upload(eq(WAV), eq("recording.wav"));
    assertThat(event.getValue().audioBytes()).isEqualTo(WAV);
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({"TOO_SHORT, AUDIO_TOO_SHORT", "TOO_LONG, AUDIO_TOO_LONG", "INVALID, AUDIO_INVALID"})
  void 변환기가_오디오를_거절하면_이유별_코드로_바로_거절하고_저장하지_않는다(
      InvalidAudioException.Reason reason, ErrorCode expected) {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    when(audioNormalizationPort.normalize(BROWSER_AUDIO))
        .thenThrow(new InvalidAudioException(reason, "rejected"));

    assertThatThrownBy(() -> service.upload(command()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", expected);

    verifyNoInteractions(storagePort);
    verify(recordingRepositoryPort, never()).save(any());
  }

  @Test
  void 변환기가_꽉_찼거나_시간을_넘기면_그대로_전달해_503이_되고_저장하지_않는다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    AudioProcessingException busy =
        new AudioProcessingException(AudioProcessingException.Reason.CAPACITY, "busy");
    when(audioNormalizationPort.normalize(BROWSER_AUDIO)).thenThrow(busy);

    // 전역 핸들러가 CAPACITY·TIMEOUT을 503 AUDIO_PROCESSING_UNAVAILABLE로 바꾼다
    assertThatThrownBy(() -> service.upload(command())).isSameAs(busy);

    verifyNoInteractions(storagePort);
    verify(recordingRepositoryPort, never()).save(any());
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
    // 거절할 요청에는 변환기 자원을 쓰지 않는다
    verifyNoInteractions(audioNormalizationPort);
  }

  @Test
  void 분석이_끝난_세션에는_업로드할_수_없고_파일도_올리지_않는다() {
    DiagnosisSession analyzed = sessionOwnedBy(userId);
    analyzed.markAnalyzed();
    when(diagnosisSessionRepositoryPort.findById(sessionId)).thenReturn(Optional.of(analyzed));

    // 도메인이 던지는 IllegalStateException은 전역 핸들러가 409(INVALID_STATE_TRANSITION)로 바꾼다
    assertThatThrownBy(() -> service.upload(command())).isInstanceOf(IllegalStateException.class);

    verify(storagePort, never()).upload(any(), any());
  }

  @Test
  void 파일을_올리는_사이_세션이_분석을_마쳤으면_녹음을_등록하지_않는다() {
    DiagnosisSession analyzedMeanwhile = sessionOwnedBy(userId);
    analyzedMeanwhile.markAnalyzed();
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    normalizerReturnsWav();
    when(storagePort.upload(any(), any())).thenReturn("recordings/abc.wav");
    when(diagnosisSessionRepositoryPort.findByIdForUpdate(sessionId))
        .thenReturn(Optional.of(analyzedMeanwhile));

    assertThatThrownBy(() -> service.upload(command())).isInstanceOf(IllegalStateException.class);

    verify(recordingRepositoryPort, never()).save(any());
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
