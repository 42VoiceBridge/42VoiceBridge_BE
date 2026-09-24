package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetRecordingResultServiceTest {

  @Mock private RecordingRepositoryPort recordingRepositoryPort;
  @Mock private SentenceRepositoryPort sentenceRepositoryPort;

  private GetRecordingResultService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID sentenceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new GetRecordingResultService(recordingRepositoryPort, sentenceRepositoryPort);
  }

  private Recording recordingOwnedBy(UUID ownerId) {
    return Recording.create(sessionId, sentenceId, ownerId, "recordings/a.wav");
  }

  private void givenSentence() {
    when(sentenceRepositoryPort.findAllByIds(List.of(sentenceId)))
        .thenReturn(List.of(new Sentence(sentenceId, "오늘 날씨가 좋습니다.")));
  }

  @Test
  void 인식이_끝나면_결과와_정답_문장을_함께_반환한다() {
    Recording recording = recordingOwnedBy(userId);
    recording.markProcessing();
    recording.markProcessed("오늘 날띠가 조습니다.", 0.62);

    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    givenSentence();

    var result = service.getResult(userId, sessionId, recording.getId());

    assertThat(result.status()).isEqualTo("DONE");
    assertThat(result.recognizedText()).isEqualTo("오늘 날띠가 조습니다.");
    assertThat(result.answerText()).isEqualTo("오늘 날씨가 좋습니다.");
    assertThat(result.confidence()).isEqualTo(0.62);
  }

  @Test
  void 인식_진행중이면_결과는_비어있고_상태만_돌아온다() {
    Recording recording = recordingOwnedBy(userId);
    recording.markProcessing();

    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    givenSentence();

    var result = service.getResult(userId, sessionId, recording.getId());

    assertThat(result.status()).isEqualTo("PROCESSING");
    assertThat(result.recognizedText()).isNull();
    assertThat(result.confidence()).isNull();
    assertThat(result.answerText()).isEqualTo("오늘 날씨가 좋습니다.");
  }

  @Test
  void 인식에_실패한_녹음은_FAILED_상태로_돌아온다() {
    Recording recording = recordingOwnedBy(userId);
    recording.markProcessing();
    recording.markFailed();

    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    givenSentence();

    var result = service.getResult(userId, sessionId, recording.getId());

    assertThat(result.status()).isEqualTo("FAILED");
    assertThat(result.recognizedText()).isNull();
  }

  @Test
  void 무음으로_인식된_경우_빈_텍스트가_그대로_반환된다() {
    Recording recording = recordingOwnedBy(userId);
    recording.markProcessing();
    recording.markProcessed("", 0.0);

    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    givenSentence();

    var result = service.getResult(userId, sessionId, recording.getId());

    assertThat(result.status()).isEqualTo("DONE");
    assertThat(result.recognizedText()).isEmpty();
  }

  @Test
  void 녹음이_없으면_예외를_던진다() {
    UUID unknownId = UUID.randomUUID();
    when(recordingRepositoryPort.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getResult(userId, sessionId, unknownId))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void 남의_녹음은_조회할_수_없다() {
    Recording recording = recordingOwnedBy(UUID.randomUUID());
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));

    assertThatThrownBy(() -> service.getResult(userId, sessionId, recording.getId()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_ACCESS);
  }

  @Test
  void 경로의_세션과_녹음의_소속이_다르면_예외를_던진다() {
    Recording recording = recordingOwnedBy(userId);
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));

    assertThatThrownBy(() -> service.getResult(userId, UUID.randomUUID(), recording.getId()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void 문장을_찾지_못하면_정답_텍스트는_null이다() {
    Recording recording = recordingOwnedBy(userId);
    when(recordingRepositoryPort.findById(recording.getId())).thenReturn(Optional.of(recording));
    when(sentenceRepositoryPort.findAllByIds(any())).thenReturn(List.of());

    var result = service.getResult(userId, sessionId, recording.getId());

    assertThat(result.answerText()).isNull();
  }
}
