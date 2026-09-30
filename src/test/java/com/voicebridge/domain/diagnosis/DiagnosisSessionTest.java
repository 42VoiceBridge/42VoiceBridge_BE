package com.voicebridge.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiagnosisSessionTest {

  private final UUID sentenceA = UUID.randomUUID();
  private final UUID sentenceB = UUID.randomUUID();

  private DiagnosisSession sessionWithTwoSentences() {
    return DiagnosisSession.start(UUID.randomUUID(), List.of(sentenceA, sentenceB));
  }

  private Recording done(DiagnosisSession session, UUID sentenceId) {
    Recording recording = processing(session, sentenceId);
    recording.markProcessed("인식 결과", null);
    return recording;
  }

  private Recording failed(DiagnosisSession session, UUID sentenceId) {
    Recording recording = processing(session, sentenceId);
    recording.markFailed();
    return recording;
  }

  private Recording processing(DiagnosisSession session, UUID sentenceId) {
    Recording recording =
        Recording.create(session.getId(), sentenceId, session.getUserId(), "recordings/a.wav");
    recording.markProcessing();
    return recording;
  }

  // 같은 문장의 녹음 순서가 중요한 테스트용. 연달아 만든 녹음은 생성 시각이 같을 수 있어 시각을 직접 정한다.
  private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 30, 10, 0);

  private Recording recordedAt(Recording recording, int minute) {
    return Recording.reconstitute(
        recording.getId(),
        recording.getSessionId(),
        recording.getSentenceId(),
        recording.getUserId(),
        recording.getS3Path(),
        recording.getStatus(),
        recording.getRecognizedText(),
        recording.getConfidence(),
        T0.plusMinutes(minute));
  }

  @Test
  void 문장_목록과_함께_시작하면_IN_PROGRESS_상태다() {
    DiagnosisSession session =
        DiagnosisSession.start(UUID.randomUUID(), List.of(UUID.randomUUID()));

    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.IN_PROGRESS);
  }

  @Test
  void 문장_목록이_비어있으면_시작할_수_없다() {
    assertThatThrownBy(() -> DiagnosisSession.start(UUID.randomUUID(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void IN_PROGRESS_상태에서만_분석완료로_전이할_수_있다() {
    DiagnosisSession session =
        DiagnosisSession.start(UUID.randomUUID(), List.of(UUID.randomUUID()));

    session.markAnalyzed();

    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.ANALYZED);
    assertThatThrownBy(session::markAnalyzed).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 문장마다_DONE_녹음이_있으면_ANALYZED로_전이한다() {
    DiagnosisSession session = sessionWithTwoSentences();

    boolean changed =
        session.markAnalyzedIfAllSentencesDone(
            List.of(done(session, sentenceA), done(session, sentenceB)));

    assertThat(changed).isTrue();
    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.ANALYZED);
  }

  @Test
  void 인식이_끝나지_않은_문장이_하나라도_있으면_전이하지_않는다() {
    DiagnosisSession session = sessionWithTwoSentences();

    boolean changed =
        session.markAnalyzedIfAllSentencesDone(
            List.of(done(session, sentenceA), processing(session, sentenceB)));

    assertThat(changed).isFalse();
    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.IN_PROGRESS);
  }

  @Test
  void 실패한_녹음이_남아있어도_같은_문장을_다시_녹음해_DONE이면_전이한다() {
    DiagnosisSession session = sessionWithTwoSentences();

    boolean changed =
        session.markAnalyzedIfAllSentencesDone(
            List.of(
                recordedAt(failed(session, sentenceA), 0),
                recordedAt(done(session, sentenceA), 1),
                done(session, sentenceB)));

    assertThat(changed).isTrue();
  }

  @Test
  void 다시_녹음한_것이_아직_인식_중이면_예전_DONE이_있어도_전이하지_않는다() {
    DiagnosisSession session = sessionWithTwoSentences();

    // 여기서 끝내면, 분석이 끝난 뒤에 다시 녹음한 것의 결과가 들어와 통계의 재료가 바뀐다
    boolean changed =
        session.markAnalyzedIfAllSentencesDone(
            List.of(
                recordedAt(done(session, sentenceA), 0),
                recordedAt(processing(session, sentenceA), 1),
                done(session, sentenceB)));

    assertThat(changed).isFalse();
  }

  @Test
  void 다시_녹음한_것이_실패했으면_예전_DONE이_있어도_전이하지_않는다() {
    DiagnosisSession session = sessionWithTwoSentences();

    // 화면에는 문장마다 가장 최근 녹음이 보인다. FAILED로 보이는 문장이 있는데 세션이 끝나면 안 된다.
    boolean changed =
        session.markAnalyzedIfAllSentencesDone(
            List.of(
                recordedAt(done(session, sentenceA), 0),
                recordedAt(failed(session, sentenceA), 1),
                done(session, sentenceB)));

    assertThat(changed).isFalse();
  }

  @Test
  void 다른_세션의_녹음은_세지_않는다() {
    DiagnosisSession session = sessionWithTwoSentences();
    DiagnosisSession other = DiagnosisSession.start(session.getUserId(), List.of(sentenceB));

    boolean changed =
        session.markAnalyzedIfAllSentencesDone(
            List.of(done(session, sentenceA), done(other, sentenceB)));

    assertThat(changed).isFalse();
  }

  @Test
  void 이미_분석된_세션이면_아무것도_하지_않는다() {
    DiagnosisSession session = sessionWithTwoSentences();
    List<Recording> recordings = List.of(done(session, sentenceA), done(session, sentenceB));
    session.markAnalyzedIfAllSentencesDone(recordings);

    assertThat(session.markAnalyzedIfAllSentencesDone(recordings)).isFalse();
    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.ANALYZED);
  }

  @Test
  void 분석이_끝난_세션에는_녹음을_추가할_수_없다() {
    DiagnosisSession session = sessionWithTwoSentences();
    session.ensureRecordable();

    session.markAnalyzed();

    assertThatThrownBy(session::ensureRecordable).isInstanceOf(IllegalStateException.class);
  }
}
