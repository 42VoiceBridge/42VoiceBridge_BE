package com.voicebridge.application;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.domain.diagnosis.JamoErrorSnapshot;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.in.GetJamoErrorStatsUseCase;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.JamoErrorSnapshotRepositoryPort;
import com.voicebridge.port.out.JamoStatsPort;
import com.voicebridge.port.out.JamoStatsPort.JamoStatsResult;
import com.voicebridge.port.out.JamoStatsPort.TextPair;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// @Transactional을 걸지 않는다. 스냅샷이 낡았으면 AI의 HTTP 응답을 기다려야 하는데, 그동안 DB 연결을 붙잡고 있지 않도록
// 조회와 저장은 각 어댑터가 따로 처리한다.
@Service
public class GetJamoErrorStatsService implements GetJamoErrorStatsUseCase {

  private final DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  private final RecordingRepositoryPort recordingRepositoryPort;
  private final SentenceRepositoryPort sentenceRepositoryPort;
  private final JamoStatsPort jamoStatsPort;
  private final JamoErrorSnapshotRepositoryPort snapshotRepositoryPort;
  private final int minSupport;

  public GetJamoErrorStatsService(
      DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort,
      RecordingRepositoryPort recordingRepositoryPort,
      SentenceRepositoryPort sentenceRepositoryPort,
      JamoStatsPort jamoStatsPort,
      JamoErrorSnapshotRepositoryPort snapshotRepositoryPort,
      @Value("${voicebridge.ai.jamo-stats.min-support}") int minSupport) {
    this.diagnosisSessionRepositoryPort = diagnosisSessionRepositoryPort;
    this.recordingRepositoryPort = recordingRepositoryPort;
    this.sentenceRepositoryPort = sentenceRepositoryPort;
    this.jamoStatsPort = jamoStatsPort;
    this.snapshotRepositoryPort = snapshotRepositoryPort;
    this.minSupport = minSupport;
  }

  @Override
  public StatsResult getStats(UUID userId) {
    List<DiagnosisSession> sessions =
        diagnosisSessionRepositoryPort.findByUserIdAndStatusIn(
            userId, DiagnosisSessionStatus.aggregated());

    // 스냅샷이 최신이면 AI를 부르지 않는다. 없거나 낡았으면 다시 계산해 저장한다. 계산이 실패해도 저장되지 않으므로 다음 조회에서
    // 자연히 다시 시도된다.
    JamoErrorSnapshot snapshot =
        snapshotRepositoryPort
            .findByUserId(userId)
            .filter(saved -> !saved.isStale(sessions.size(), minSupport))
            .orElseGet(() -> snapshotRepositoryPort.save(recompute(userId, sessions)));

    return new StatsResult(
        snapshot.getMetricVersion(),
        snapshot.getMinSupport(),
        snapshot.getSessionsUsed(),
        snapshot.getPairsUsed(),
        snapshot.getTokens());
  }

  private JamoErrorSnapshot recompute(UUID userId, List<DiagnosisSession> sessions) {
    List<TextPair> pairs = pairsFrom(sessions);
    // AI는 빈 쌍을 거절(422)하므로 부르지 않고 빈 스냅샷으로 대신한다
    if (pairs.isEmpty()) {
      return JamoErrorSnapshot.empty(userId, minSupport, sessions.size());
    }
    JamoStatsResult result = jamoStatsPort.analyze(pairs, minSupport);
    return JamoErrorSnapshot.create(
        userId,
        result.metricVersion(),
        result.minSupport(),
        sessions.size(),
        result.pairsUsed(),
        result.tokens());
  }

  /**
   * 세션의 문장마다 가장 최근 녹음 하나를 쓰고, 그게 무음이면 그 문장은 뺀다. 세션 완료 판단과 같은 녹음을 봐야 분석이 끝난 세션의 재료가 바뀌지 않는다(그래서 낡음
   * 검사가 세션 수만 봐도 된다). 예전 녹음으로 대신 채우지 않는 것도 같은 이유다. 정답은 등록 문장의 원문을 쓴다. 모델 출력을 정답 자리에 넣으면 모델이 틀린 것을
   * 맞았다고 세게 된다.
   */
  private List<TextPair> pairsFrom(List<DiagnosisSession> sessions) {
    if (sessions.isEmpty()) {
      return List.of();
    }
    List<UUID> sessionIds = sessions.stream().map(DiagnosisSession::getId).toList();

    List<Recording> latest =
        Recording.latestPerSentence(recordingRepositoryPort.findBySessionIdIn(sessionIds)).stream()
            .filter(Recording::isUsableForJamoStats)
            .toList();
    if (latest.isEmpty()) {
      return List.of();
    }

    Map<UUID, String> answerById =
        sentenceRepositoryPort
            .findAllByIds(latest.stream().map(Recording::getSentenceId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Sentence::id, Sentence::text));

    return latest.stream()
        // 원문을 찾지 못한 문장은 정답을 알 수 없으니 뺀다
        .filter(r -> answerById.containsKey(r.getSentenceId()))
        .map(r -> new TextPair(answerById.get(r.getSentenceId()), r.getRecognizedText()))
        .toList();
  }
}
