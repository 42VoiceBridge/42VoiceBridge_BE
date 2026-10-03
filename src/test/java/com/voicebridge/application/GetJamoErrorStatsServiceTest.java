package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.domain.diagnosis.JamoErrorSnapshot;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.RecordingStatus;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.in.GetJamoErrorStatsUseCase.StatsResult;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.JamoErrorSnapshotRepositoryPort;
import com.voicebridge.port.out.JamoStatsPort;
import com.voicebridge.port.out.JamoStatsPort.JamoStatsResult;
import com.voicebridge.port.out.JamoStatsPort.TextPair;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
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

@ExtendWith(MockitoExtension.class)
class GetJamoErrorStatsServiceTest {

  private static final int MIN_SUPPORT = 20;

  @Mock private DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  @Mock private RecordingRepositoryPort recordingRepositoryPort;
  @Mock private SentenceRepositoryPort sentenceRepositoryPort;
  @Mock private JamoStatsPort jamoStatsPort;
  @Mock private JamoErrorSnapshotRepositoryPort snapshotRepositoryPort;

  private GetJamoErrorStatsService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID sentenceA = UUID.randomUUID();
  private final UUID sentenceB = UUID.randomUUID();
  private final LocalDateTime t0 = LocalDateTime.of(2026, 9, 28, 10, 0);
  private final JamoErrorStat stat = new JamoErrorStat("ㅈ", "INITIAL", 12, 20, 0.6, "OK");

  @BeforeEach
  void setUp() {
    service =
        new GetJamoErrorStatsService(
            diagnosisSessionRepositoryPort,
            recordingRepositoryPort,
            sentenceRepositoryPort,
            jamoStatsPort,
            snapshotRepositoryPort,
            MIN_SUPPORT);
  }

  private DiagnosisSession analyzedSession() {
    return DiagnosisSession.reconstitute(
        UUID.randomUUID(),
        userId,
        DiagnosisSessionStatus.ANALYZED,
        List.of(sentenceA, sentenceB),
        t0);
  }

  private Recording recording(
      DiagnosisSession session,
      UUID sentenceId,
      RecordingStatus status,
      String text,
      LocalDateTime createdAt) {
    return Recording.reconstitute(
        UUID.randomUUID(),
        session.getId(),
        sentenceId,
        userId,
        "recordings/a.wav",
        status,
        text,
        null,
        createdAt);
  }

  private void givenSessions(DiagnosisSession... sessions) {
    when(diagnosisSessionRepositoryPort.findByUserIdAndStatusIn(
            userId, DiagnosisSessionStatus.aggregated()))
        .thenReturn(List.of(sessions));
  }

  private void givenNoSnapshot() {
    when(snapshotRepositoryPort.findByUserId(userId)).thenReturn(Optional.empty());
  }

  private void givenRecordings(Recording... recordings) {
    when(recordingRepositoryPort.findBySessionIdIn(anyList())).thenReturn(List.of(recordings));
  }

  private void givenSentences() {
    when(sentenceRepositoryPort.findAllByIds(anyList()))
        .thenReturn(
            List.of(new Sentence(sentenceA, "오늘 날씨가 좋습니다"), new Sentence(sentenceB, "바람이 붑니다")));
  }

  private void givenSaveReturnsInput() {
    when(snapshotRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private List<TextPair> capturePairs() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<TextPair>> captor = ArgumentCaptor.forClass(List.class);
    verify(jamoStatsPort).analyze(captor.capture(), eq(MIN_SUPPORT));
    return captor.getValue();
  }

  @Test
  void 스냅샷이_최신이면_AI를_부르지_않고_그대로_돌려준다() {
    givenSessions(analyzedSession());
    when(snapshotRepositoryPort.findByUserId(userId))
        .thenReturn(
            Optional.of(
                JamoErrorSnapshot.create(userId, "jamo-err-v1", MIN_SUPPORT, 1, 5, List.of(stat))));

    StatsResult result = service.getStats(userId);

    assertThat(result.tokens()).containsExactly(stat);
    verify(jamoStatsPort, never()).analyze(anyList(), anyInt());
    verify(snapshotRepositoryPort, never()).save(any());
  }

  @Test
  void 스냅샷이_없으면_문장마다_가장_최근의_인식_결과로_계산해_저장한다() {
    DiagnosisSession session = analyzedSession();
    givenSessions(session);
    givenNoSnapshot();
    givenRecordings(
        recording(session, sentenceA, RecordingStatus.DONE, "오늘 날씨가 조습니다", t0),
        recording(session, sentenceA, RecordingStatus.DONE, "오늘 날씨가 좋습니다", t0.plusMinutes(1)),
        recording(session, sentenceB, RecordingStatus.DONE, "바라미 붑니다", t0));
    givenSentences();
    givenSaveReturnsInput();
    when(jamoStatsPort.analyze(anyList(), eq(MIN_SUPPORT)))
        .thenReturn(new JamoStatsResult("jamo-err-v1", MIN_SUPPORT, 2, List.of(stat)));

    StatsResult result = service.getStats(userId);

    // 정답 자리에는 인식 결과가 아니라 문장 원문이 들어가야 한다
    assertThat(capturePairs())
        .containsExactlyInAnyOrder(
            new TextPair("오늘 날씨가 좋습니다", "오늘 날씨가 좋습니다"), new TextPair("바람이 붑니다", "바라미 붑니다"));
    assertThat(result.metricVersion()).isEqualTo("jamo-err-v1");
    assertThat(result.sessionsUsed()).isEqualTo(1);
    assertThat(result.pairsUsed()).isEqualTo(2);
    assertThat(result.tokens()).containsExactly(stat);
    verify(snapshotRepositoryPort).save(any());
  }

  @Test
  void 분석한_세션이_늘었으면_다시_계산한다() {
    DiagnosisSession first = analyzedSession();
    DiagnosisSession second = analyzedSession();
    givenSessions(first, second);
    when(snapshotRepositoryPort.findByUserId(userId))
        .thenReturn(
            Optional.of(
                JamoErrorSnapshot.create(userId, "jamo-err-v1", MIN_SUPPORT, 1, 2, List.of(stat))));
    givenRecordings(recording(second, sentenceA, RecordingStatus.DONE, "오늘 날씨가 좋습니다", t0));
    givenSentences();
    givenSaveReturnsInput();
    when(jamoStatsPort.analyze(anyList(), eq(MIN_SUPPORT)))
        .thenReturn(new JamoStatsResult("jamo-err-v1", MIN_SUPPORT, 1, List.of(stat)));

    StatsResult result = service.getStats(userId);

    assertThat(result.sessionsUsed()).isEqualTo(2);
    verify(jamoStatsPort).analyze(anyList(), eq(MIN_SUPPORT));
  }

  @Test
  void 무음으로_인식된_녹음은_쌍에서_뺀다() {
    DiagnosisSession session = analyzedSession();
    givenSessions(session);
    givenNoSnapshot();
    givenRecordings(
        recording(session, sentenceA, RecordingStatus.DONE, "", t0),
        recording(session, sentenceB, RecordingStatus.DONE, "바라미 붑니다", t0));
    givenSentences();
    givenSaveReturnsInput();
    when(jamoStatsPort.analyze(anyList(), eq(MIN_SUPPORT)))
        .thenReturn(new JamoStatsResult("jamo-err-v1", MIN_SUPPORT, 1, List.of(stat)));

    service.getStats(userId);

    assertThat(capturePairs()).containsExactly(new TextPair("바람이 붑니다", "바라미 붑니다"));
  }

  @Test
  void 가장_최근_녹음이_무음이면_예전_녹음으로_대신하지_않는다() {
    DiagnosisSession session = analyzedSession();
    givenSessions(session);
    givenNoSnapshot();
    givenRecordings(
        recording(session, sentenceA, RecordingStatus.DONE, "오늘 날씨가 조습니다", t0),
        recording(session, sentenceA, RecordingStatus.DONE, "", t0.plusMinutes(1)),
        recording(session, sentenceB, RecordingStatus.DONE, "바라미 붑니다", t0));
    givenSentences();
    givenSaveReturnsInput();
    when(jamoStatsPort.analyze(anyList(), eq(MIN_SUPPORT)))
        .thenReturn(new JamoStatsResult("jamo-err-v1", MIN_SUPPORT, 1, List.of(stat)));

    service.getStats(userId);

    // 세션 완료 판단도 가장 최근 녹음만 본다. 여기서 예전 녹음을 쓰면 두 판단이 서로 다른 녹음을 보게 된다.
    assertThat(capturePairs()).containsExactly(new TextPair("바람이 붑니다", "바라미 붑니다"));
  }

  @Test
  void 같은_문장이_다른_세션에도_있으면_각각의_쌍이_된다() {
    DiagnosisSession first = analyzedSession();
    DiagnosisSession second = analyzedSession();
    givenSessions(first, second);
    givenNoSnapshot();
    givenRecordings(
        recording(first, sentenceA, RecordingStatus.DONE, "오늘 날씨가 조습니다", t0),
        recording(second, sentenceA, RecordingStatus.DONE, "오늘 날씨가 좋습니다", t0));
    givenSentences();
    givenSaveReturnsInput();
    when(jamoStatsPort.analyze(anyList(), eq(MIN_SUPPORT)))
        .thenReturn(new JamoStatsResult("jamo-err-v1", MIN_SUPPORT, 2, List.of(stat)));

    service.getStats(userId);

    assertThat(capturePairs()).hasSize(2);
  }

  @Test
  void 분석한_세션이_없으면_AI를_부르지_않고_빈_결과를_돌려준다() {
    givenSessions();
    givenNoSnapshot();
    givenSaveReturnsInput();

    StatsResult result = service.getStats(userId);

    assertThat(result.sessionsUsed()).isZero();
    assertThat(result.metricVersion()).isNull();
    assertThat(result.tokens()).isEmpty();
    verify(recordingRepositoryPort, never()).findBySessionIdIn(anyList());
    verify(jamoStatsPort, never()).analyze(anyList(), anyInt());
  }

  @Test
  void 쓸_수_있는_녹음이_없으면_AI를_부르지_않는다() {
    DiagnosisSession session = analyzedSession();
    givenSessions(session);
    givenNoSnapshot();
    givenRecordings(recording(session, sentenceA, RecordingStatus.DONE, "", t0));
    givenSaveReturnsInput();

    StatsResult result = service.getStats(userId);

    assertThat(result.sessionsUsed()).isEqualTo(1);
    assertThat(result.pairsUsed()).isZero();
    verify(jamoStatsPort, never()).analyze(anyList(), anyInt());
  }

  @Test
  void AI_계산이_실패하면_스냅샷을_저장하지_않아_다음_조회에서_다시_시도한다() {
    DiagnosisSession session = analyzedSession();
    givenSessions(session);
    givenNoSnapshot();
    givenRecordings(recording(session, sentenceA, RecordingStatus.DONE, "오늘 날씨가 좋습니다", t0));
    givenSentences();
    when(jamoStatsPort.analyze(anyList(), eq(MIN_SUPPORT)))
        .thenThrow(new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE));

    assertThatThrownBy(() -> service.getStats(userId))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_INFERENCE_UNAVAILABLE);
    verify(snapshotRepositoryPort, never()).save(any());
  }
}
