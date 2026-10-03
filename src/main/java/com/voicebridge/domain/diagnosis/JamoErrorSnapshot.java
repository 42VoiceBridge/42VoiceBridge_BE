package com.voicebridge.domain.diagnosis;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 사용자의 분석 완료 세션들을 누적해 계산한 자모 오류 통계의 저장본. 계산은 AI가 하고 백엔드는 결과를 보관한다(AI 계약 §1). 사용자당 최신 하나만 둔다. */
public class JamoErrorSnapshot {

  private final UUID userId;
  private final String metricVersion;
  private final int minSupport;
  private final int sessionsUsed;
  private final int pairsUsed;
  private final List<JamoErrorStat> tokens;
  private final LocalDateTime computedAt;

  private JamoErrorSnapshot(
      UUID userId,
      String metricVersion,
      int minSupport,
      int sessionsUsed,
      int pairsUsed,
      List<JamoErrorStat> tokens,
      LocalDateTime computedAt) {
    this.userId = userId;
    this.metricVersion = metricVersion;
    this.minSupport = minSupport;
    this.sessionsUsed = sessionsUsed;
    this.pairsUsed = pairsUsed;
    this.tokens = tokens;
    this.computedAt = computedAt;
  }

  /** AI가 계산한 결과로 새 스냅샷을 만든다. */
  public static JamoErrorSnapshot create(
      UUID userId,
      String metricVersion,
      int minSupport,
      int sessionsUsed,
      int pairsUsed,
      List<JamoErrorStat> tokens) {
    if (userId == null || metricVersion == null || tokens == null) {
      throw new IllegalArgumentException("자모 오류 통계에는 사용자, 계산 버전, 자모 목록이 필요합니다.");
    }
    return new JamoErrorSnapshot(
        userId,
        metricVersion,
        minSupport,
        sessionsUsed,
        pairsUsed,
        List.copyOf(tokens),
        LocalDateTime.now());
  }

  /**
   * AI에 보낼 쌍이 하나도 없을 때의 스냅샷. 분석한 세션이 없거나, 있어도 쓸 수 있는 녹음이 없는 경우다. AI가 빈 쌍을 거절하므로 호출하지 않고 이것으로 대신한다.
   * 계산하지 않았으니 metricVersion은 null이다.
   */
  public static JamoErrorSnapshot empty(UUID userId, int minSupport, int sessionsUsed) {
    if (userId == null) {
      throw new IllegalArgumentException("자모 오류 통계에는 사용자가 필요합니다.");
    }
    return new JamoErrorSnapshot(
        userId, null, minSupport, sessionsUsed, 0, List.of(), LocalDateTime.now());
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
  public static JamoErrorSnapshot reconstitute(
      UUID userId,
      String metricVersion,
      int minSupport,
      int sessionsUsed,
      int pairsUsed,
      List<JamoErrorStat> tokens,
      LocalDateTime computedAt) {
    return new JamoErrorSnapshot(
        userId, metricVersion, minSupport, sessionsUsed, pairsUsed, tokens, computedAt);
  }

  /**
   * 이 스냅샷을 만든 뒤 분석 완료 세션이 늘었거나 기준 표본 수가 바뀌었으면 다시 계산해야 한다. 조회할 때마다 이걸 확인하므로, 계산이 한 번 실패해도 다음 조회에서
   * 자연히 다시 시도된다.
   *
   * <p>세션 수만 봐도 되는 이유: 분석이 끝난(ANALYZED) 세션의 통계 재료는 더 바뀌지 않는다. 세션은 문장마다 가장 최근 녹음이 끝나야 ANALYZED가 되고,
   * 그 뒤로는 녹음을 받지 않으며, 통계도 같은 가장 최근 녹음만 쓴다({@link Recording#latestPerSentence}).
   */
  public boolean isStale(int analyzedSessionCount, int minSupport) {
    return sessionsUsed != analyzedSessionCount || this.minSupport != minSupport;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getMetricVersion() {
    return metricVersion;
  }

  public int getMinSupport() {
    return minSupport;
  }

  public int getSessionsUsed() {
    return sessionsUsed;
  }

  public int getPairsUsed() {
    return pairsUsed;
  }

  public List<JamoErrorStat> getTokens() {
    return tokens;
  }

  public LocalDateTime getComputedAt() {
    return computedAt;
  }
}
