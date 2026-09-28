package com.voicebridge.port.in;

import com.voicebridge.domain.diagnosis.JamoErrorStat;
import java.util.List;
import java.util.UUID;

/** API 명세서 2.5절. 사용자의 분석 완료 세션들을 누적한 자모 오류 통계를 조회한다. 세션 하나로는 자모별 표본이 모이지 않아 세션 단위가 아니라 사용자 단위다. */
public interface GetJamoErrorStatsUseCase {

  StatsResult getStats(UUID userId);

  /**
   * 표본이 부족하면 tokens가 비어 있거나 해당 자모가 INSUFFICIENT_DATA로 온다. 프론트는 sessionsUsed로 "세션을 더 진행해 주세요" 같은 안내를
   * 띄운다.
   *
   * @param metricVersion 계산할 쌍이 없어 AI를 부르지 않았으면 null
   */
  record StatsResult(
      String metricVersion,
      int minSupport,
      int sessionsUsed,
      int pairsUsed,
      List<JamoErrorStat> tokens) {}
}
