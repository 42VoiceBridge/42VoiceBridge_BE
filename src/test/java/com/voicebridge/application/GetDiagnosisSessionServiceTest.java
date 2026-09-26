package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDiagnosisSessionServiceTest {

  @Mock private DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  @Mock private SentenceRepositoryPort sentenceRepositoryPort;
  @Mock private RecordingRepositoryPort recordingRepositoryPort;

  private GetDiagnosisSessionService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID firstSentenceId = UUID.randomUUID();
  private final UUID secondSentenceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new GetDiagnosisSessionService(
            diagnosisSessionRepositoryPort, sentenceRepositoryPort, recordingRepositoryPort);
  }

  private DiagnosisSession sessionOwnedBy(UUID ownerId) {
    return DiagnosisSession.reconstitute(
        sessionId,
        ownerId,
        DiagnosisSessionStatus.IN_PROGRESS,
        List.of(firstSentenceId, secondSentenceId),
        LocalDateTime.now());
  }

  private void givenSentences() {
    when(sentenceRepositoryPort.findAllByIds(List.of(firstSentenceId, secondSentenceId)))
        .thenReturn(
            List.of(
                new Sentence(firstSentenceId, "오늘 날씨가 좋습니다."),
                new Sentence(secondSentenceId, "감사합니다.")));
  }

  @Test
  void 녹음하지_않은_문장은_녹음_정보가_비어있다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    givenSentences();
    when(recordingRepositoryPort.findBySessionId(sessionId)).thenReturn(List.of());

    var result = service.getSession(userId, sessionId);

    assertThat(result.sentences()).hasSize(2);
    assertThat(result.sentences()).allMatch(s -> s.recordingId() == null);
    assertThat(result.sentences()).allMatch(s -> s.recordingStatus() == null);
  }

  @Test
  void 문장_순서가_세션에_정의된_순서를_따른다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    givenSentences();
    when(recordingRepositoryPort.findBySessionId(sessionId)).thenReturn(List.of());

    var result = service.getSession(userId, sessionId);

    assertThat(result.sentences().get(0).sentenceId()).isEqualTo(firstSentenceId);
    assertThat(result.sentences().get(0).text()).isEqualTo("오늘 날씨가 좋습니다.");
    assertThat(result.sentences().get(1).sentenceId()).isEqualTo(secondSentenceId);
  }

  @Test
  void 녹음한_문장은_상태가_함께_조회된다() {
    Recording recording = Recording.create(sessionId, firstSentenceId, userId, "recordings/a.wav");
    recording.markProcessing();

    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    givenSentences();
    when(recordingRepositoryPort.findBySessionId(sessionId)).thenReturn(List.of(recording));

    var result = service.getSession(userId, sessionId);

    assertThat(result.sentences().get(0).recordingId()).isEqualTo(recording.getId());
    assertThat(result.sentences().get(0).recordingStatus()).isEqualTo("PROCESSING");
    assertThat(result.sentences().get(1).recordingId()).isNull();
  }

  @Test
  void 같은_문장을_다시_녹음하면_최신_녹음이_노출된다() {
    Recording older =
        Recording.reconstitute(
            UUID.randomUUID(),
            sessionId,
            firstSentenceId,
            userId,
            "recordings/old.wav",
            com.voicebridge.domain.diagnosis.RecordingStatus.FAILED,
            null,
            null,
            LocalDateTime.now().minusMinutes(5));
    Recording newer =
        Recording.reconstitute(
            UUID.randomUUID(),
            sessionId,
            firstSentenceId,
            userId,
            "recordings/new.wav",
            com.voicebridge.domain.diagnosis.RecordingStatus.DONE,
            "오늘 날씨가 좋습니다.",
            0.9,
            LocalDateTime.now());

    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(userId)));
    givenSentences();
    when(recordingRepositoryPort.findBySessionId(sessionId)).thenReturn(List.of(older, newer));

    var result = service.getSession(userId, sessionId);

    assertThat(result.sentences().get(0).recordingId()).isEqualTo(newer.getId());
    assertThat(result.sentences().get(0).recordingStatus()).isEqualTo("DONE");
  }

  @Test
  void 세션이_없으면_예외를_던진다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSession(userId, sessionId))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void 남의_세션은_조회할_수_없다() {
    when(diagnosisSessionRepositoryPort.findById(sessionId))
        .thenReturn(Optional.of(sessionOwnedBy(UUID.randomUUID())));

    assertThatThrownBy(() -> service.getSession(userId, sessionId))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_ACCESS);
  }
}
