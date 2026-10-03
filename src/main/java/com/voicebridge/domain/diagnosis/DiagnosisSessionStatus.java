package com.voicebridge.domain.diagnosis;

import java.util.Set;

public enum DiagnosisSessionStatus {
  IN_PROGRESS,
  ANALYZED;

  /** 자모 오류 통계의 집계 대상인 상태. 문장마다 가장 최근 녹음이 DONE이 된 세션이다. */
  public static Set<DiagnosisSessionStatus> aggregated() {
    return Set.of(ANALYZED);
  }
}
