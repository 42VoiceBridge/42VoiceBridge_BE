package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.adapter.out.audio.FfmpegAudioNormalizer;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.RecordingStatus;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.support.AudioFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

@Tag("ffmpeg")
class DiagnosisAudioNormalizationIntegrationTest {
  @TempDir Path directory;

  private RecordingRepositoryPort recordings;
  private AiInferenceClient ai;
  private ApplicationEventPublisher events;
  private RecordingRecognitionHandler handler;
  private Recording recording;

  @BeforeEach
  void setUp() throws Exception {
    recordings = mock(RecordingRepositoryPort.class);
    ai = mock(AiInferenceClient.class);
    events = mock(ApplicationEventPublisher.class);
    var work = Files.createDirectory(directory.resolve("work"));
    var normalizer =
        new FfmpegAudioNormalizer(
            new ObjectMapper(),
            new FfmpegAudioNormalizer.Settings(
                "ffmpeg",
                "ffprobe",
                work,
                Duration.ofSeconds(20),
                Duration.ofSeconds(2),
                2,
                10 * 1024 * 1024,
                true));
    handler = new RecordingRecognitionHandler(recordings, ai, normalizer, events);
    recording =
        Recording.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "recordings/a.webm");
    recording.markProcessing();
    when(recordings.findById(recording.getId())).thenReturn(Optional.of(recording));
  }

  @Test
  void webmIsConvertedToCanonicalWavBeforeInference() throws Exception {
    byte[] source = AudioFixtures.encoded(directory, "webm", "libopus", 2);
    when(ai.recognize(any(), eq(ModelType.BASE_ADAPTED), eq(recording.getUserId())))
        .thenReturn(new AiInferenceClient.RecognitionResult("안녕하세요", null));

    handler.handle(new RecordingUploadedEvent(recording.getId(), source));

    var audio = ArgumentCaptor.forClass(byte[].class);
    verify(ai).recognize(audio.capture(), eq(ModelType.BASE_ADAPTED), eq(recording.getUserId()));
    assertThat(AudioFixtures.assertCanonicalWav(audio.getValue())).isBetween(7800, 8500);
    assertThat(audio.getValue()).isNotEqualTo(source);
    assertThat(saved().getStatus()).isEqualTo(RecordingStatus.DONE);
    verify(events).publishEvent(new RecordingRecognizedEvent(recording.getSessionId()));
  }

  @Test
  void unsupportedFormatNeverReachesInference() throws Exception {
    assertFailedWithoutInference(AudioFixtures.encoded(directory, "mp3", "libmp3lame", 1));
  }

  @Test
  void damagedAudioNeverReachesInference() {
    byte[] valid = AudioFixtures.wav(16000, 1, 8000);
    assertFailedWithoutInference(Arrays.copyOf(valid, valid.length - 2000));
  }

  @Test
  void overThirtySecondsNeverReachesInference() {
    assertFailedWithoutInference(AudioFixtures.wav(16000, 1, 480001));
  }

  private void assertFailedWithoutInference(byte[] source) {
    handler.handle(new RecordingUploadedEvent(recording.getId(), source));

    assertThat(saved().getStatus()).isEqualTo(RecordingStatus.FAILED);
    verify(ai, never()).recognize(any(), any(), any());
    verify(events, never()).publishEvent(any(Object.class));
  }

  private Recording saved() {
    var captor = ArgumentCaptor.forClass(Recording.class);
    verify(recordings).save(captor.capture());
    return captor.getValue();
  }
}
