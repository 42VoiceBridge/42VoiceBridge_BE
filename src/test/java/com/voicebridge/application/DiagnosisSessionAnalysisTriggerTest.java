package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnosisSessionAnalysisTriggerTest {

  @Mock private DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  @Mock private RecordingRepositoryPort recordingRepositoryPort;

  private DiagnosisSessionAnalysisTrigger trigger;

  private final UUID sentenceA = UUID.randomUUID();
  private final UUID sentenceB = UUID.randomUUID();
  private final DiagnosisSession session =
      DiagnosisSession.start(UUID.randomUUID(), List.of(sentenceA, sentenceB));

  @BeforeEach
  void setUp() {
    trigger =
        new DiagnosisSessionAnalysisTrigger(
            diagnosisSessionRepositoryPort, recordingRepositoryPort);
  }

  private void givenRecordings(Recording... recordings) {
    when(diagnosisSessionRepositoryPort.findById(session.getId())).thenReturn(Optional.of(session));
    when(recordingRepositoryPort.findBySessionId(session.getId())).thenReturn(List.of(recordings));
  }

  private Recording recording(UUID sentenceId, boolean done) {
    Recording recording =
        Recording.create(session.getId(), sentenceId, session.getUserId(), "recordings/a.wav");
    recording.markProcessing();
    if (done) {
      recording.markProcessed("인식 결과", null);
    }
    return recording;
  }

  @Test
  void 모든_문장이_인식되면_세션을_ANALYZED로_저장한다() {
    givenRecordings(recording(sentenceA, true), recording(sentenceB, true));

    trigger.onRecordingRecognized(new RecordingRecognizedEvent(session.getId()));

    verify(diagnosisSessionRepositoryPort).save(session);
    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.ANALYZED);
  }

  @Test
  void 아직_인식_중인_문장이_있으면_저장하지_않는다() {
    givenRecordings(recording(sentenceA, true), recording(sentenceB, false));

    trigger.onRecordingRecognized(new RecordingRecognizedEvent(session.getId()));

    verify(diagnosisSessionRepositoryPort, never()).save(any());
  }

  @Test
  void 세션을_찾지_못하면_아무것도_하지_않는다() {
    UUID unknown = UUID.randomUUID();
    when(diagnosisSessionRepositoryPort.findById(unknown)).thenReturn(Optional.empty());

    trigger.onRecordingRecognized(new RecordingRecognizedEvent(unknown));

    verify(recordingRepositoryPort, never()).findBySessionId(any());
    verify(diagnosisSessionRepositoryPort, never()).save(any());
  }
}
