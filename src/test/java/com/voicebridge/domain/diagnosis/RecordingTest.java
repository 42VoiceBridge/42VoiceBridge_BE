package com.voicebridge.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordingTest {

  private Recording newRecording() {
    return Recording.create(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "recordings/sample.wav");
  }

  @Test
  void 생성하면_UPLOADED_상태다() {
    Recording recording = newRecording();

    assertThat(recording.getStatus()).isEqualTo(RecordingStatus.UPLOADED);
    assertThat(recording.getRecognizedText()).isNull();
    assertThat(recording.getConfidence()).isNull();
  }

  @Test
  void 저장_경로가_없으면_생성할_수_없다() {
    assertThatThrownBy(
            () -> Recording.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 소속_정보가_없으면_생성할_수_없다() {
    assertThatThrownBy(
            () -> Recording.create(null, UUID.randomUUID(), UUID.randomUUID(), "recordings/a.wav"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void UPLOADED에서_PROCESSING을_거쳐_DONE으로_전이된다() {
    Recording recording = newRecording();

    recording.markProcessing();
    assertThat(recording.getStatus()).isEqualTo(RecordingStatus.PROCESSING);

    recording.markProcessed("오늘 날씨가 좋습니다.", 0.87);

    assertThat(recording.getStatus()).isEqualTo(RecordingStatus.DONE);
    assertThat(recording.getRecognizedText()).isEqualTo("오늘 날씨가 좋습니다.");
    assertThat(recording.getConfidence()).isEqualTo(0.87);
    assertThat(recording.isDone()).isTrue();
  }

  @Test
  void 인식을_시작하지_않은_녹음에는_결과를_반영할_수_없다() {
    Recording recording = newRecording();

    assertThatThrownBy(() -> recording.markProcessed("오늘 날씨가 좋습니다.", 0.87))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 이미_인식이_시작된_녹음은_다시_시작할_수_없다() {
    Recording recording = newRecording();
    recording.markProcessing();

    assertThatThrownBy(recording::markProcessing).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 무음이면_빈_텍스트로_인식_완료된다() {
    Recording recording = newRecording();
    recording.markProcessing();

    recording.markProcessed("", 0.0);

    assertThat(recording.getStatus()).isEqualTo(RecordingStatus.DONE);
    assertThat(recording.getRecognizedText()).isEmpty();
    assertThat(recording.isDone()).isTrue();
  }

  @Test
  void 신뢰도가_null이면_범위_검증_없이_반영된다() {
    Recording recording = newRecording();
    recording.markProcessing();

    recording.markProcessed("오늘 날씨가 좋습니다.", null);

    assertThat(recording.getStatus()).isEqualTo(RecordingStatus.DONE);
    assertThat(recording.getConfidence()).isNull();
  }

  @Test
  void 인식_결과가_null이면_반영할_수_없다() {
    Recording recording = newRecording();
    recording.markProcessing();

    assertThatThrownBy(() -> recording.markProcessed(null, 0.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 신뢰도가_0과_1_사이를_벗어나면_반영할_수_없다() {
    Recording recording = newRecording();
    recording.markProcessing();

    assertThatThrownBy(() -> recording.markProcessed("오늘 날씨가 좋습니다.", 1.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> recording.markProcessed("오늘 날씨가 좋습니다.", -0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 인식이_진행중인_녹음은_실패_처리할_수_있다() {
    Recording recording = newRecording();
    recording.markProcessing();

    recording.markFailed();

    assertThat(recording.getStatus()).isEqualTo(RecordingStatus.FAILED);
    assertThat(recording.isDone()).isFalse();
  }

  @Test
  void 인식을_시작하지_않은_녹음은_실패_처리할_수_없다() {
    Recording recording = newRecording();

    assertThatThrownBy(recording::markFailed).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 실패한_녹음에는_결과를_반영할_수_없다() {
    Recording recording = newRecording();
    recording.markProcessing();
    recording.markFailed();

    assertThatThrownBy(() -> recording.markProcessed("오늘 날씨가 좋습니다.", 0.87))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 소유자를_확인할_수_있다() {
    UUID userId = UUID.randomUUID();
    Recording recording =
        Recording.create(UUID.randomUUID(), UUID.randomUUID(), userId, "recordings/a.wav");

    assertThat(recording.isOwnedBy(userId)).isTrue();
    assertThat(recording.isOwnedBy(UUID.randomUUID())).isFalse();
  }

  @Test
  void 자모_통계에는_인식이_끝났고_무음이_아닌_녹음만_쓴다() {
    Recording spoken = newRecording();
    spoken.markProcessing();
    assertThat(spoken.isUsableForJamoStats()).isFalse();
    spoken.markProcessed("오늘 날씨가 좋습니다.", null);
    assertThat(spoken.isUsableForJamoStats()).isTrue();

    Recording silent = newRecording();
    silent.markProcessing();
    silent.markProcessed("", null);
    assertThat(silent.isUsableForJamoStats()).isFalse();
  }

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 30, 10, 0);

  private Recording recordedAt(UUID sessionId, UUID sentenceId, LocalDateTime createdAt) {
    return Recording.reconstitute(
        UUID.randomUUID(),
        sessionId,
        sentenceId,
        UUID.randomUUID(),
        "recordings/a.wav",
        RecordingStatus.DONE,
        "인식 결과",
        null,
        createdAt);
  }

  @Test
  void 문장마다_가장_늦게_만든_녹음만_남기고_세션이_다르면_따로_남긴다() {
    UUID session = UUID.randomUUID();
    UUID otherSession = UUID.randomUUID();
    UUID sentence = UUID.randomUUID();
    Recording first = recordedAt(session, sentence, T0);
    Recording retake = recordedAt(session, sentence, T0.plusMinutes(1));
    Recording inOtherSession = recordedAt(otherSession, sentence, T0);

    assertThat(Recording.latestPerSentence(List.of(retake, first, inOtherSession)))
        .containsExactlyInAnyOrder(retake, inOtherSession);
  }

  @Test
  void 생성_시각이_같아도_조회_순서와_상관없이_같은_녹음을_고른다() {
    UUID session = UUID.randomUUID();
    UUID sentence = UUID.randomUUID();
    Recording a = recordedAt(session, sentence, T0);
    Recording b = recordedAt(session, sentence, T0);

    // 세션 완료 판단과 통계는 서로 다른 쿼리로 녹음을 읽는다. 순서가 달라도 같은 답이어야 한다.
    assertThat(Recording.latestPerSentence(List.of(a, b)))
        .isEqualTo(Recording.latestPerSentence(List.of(b, a)));
  }
}
