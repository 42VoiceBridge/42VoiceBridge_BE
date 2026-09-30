package com.voicebridge.domain.diagnosis;

import java.util.Set;

public enum DiagnosisSessionStatus {
  IN_PROGRESS,
  ANALYZED,
  COMPLETED;

  /** 자모 오류 통계의 집계 대상인 상태. COMPLETED는 ANALYZED 다음 단계라 여전히 녹음이 모두 끝난 세션이다. */
  public static Set<DiagnosisSessionStatus> aggregated() {
    return Set.of(ANALYZED, COMPLETED);
  }
}
