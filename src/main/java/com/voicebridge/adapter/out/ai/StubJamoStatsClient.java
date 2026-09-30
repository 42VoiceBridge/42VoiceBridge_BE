package com.voicebridge.adapter.out.ai;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import com.voicebridge.port.out.JamoStatsPort;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 로컬 개발 전용. 고정값이 아니라 받은 쌍 수에 비례해 표본을 만든다 — 세션이 쌓일수록 INSUFFICIENT_DATA가 OK로 바뀌는 흐름을
// AI 서버 없이도 화면에서 확인할 수 있게 하려는 것이다.
@Slf4j
@Component
@Profile("local")
public class StubJamoStatsClient implements JamoStatsPort {

  @Override
  public JamoStatsResult analyze(List<TextPair> pairs, int minSupport) {
    // 실제 AI는 빈 쌍을 422로 거절한다. 스텁이 받아주면 서비스가 빈 쌍을 거르지 않아도 로컬에서는 드러나지 않는다.
    if (pairs.isEmpty()) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE, "자모 오류 통계를 계산하지 못했습니다.");
    }
    log.info("[스텁 자모 통계] 실제 AI를 호출하지 않는다. pairs={} minSupport={}", pairs.size(), minSupport);

    int n = pairs.size();
    return new JamoStatsResult(
        "jamo-err-v1",
        minSupport,
        n,
        List.of(
            token("ㅈ", "INITIAL", n, n * 2, minSupport),
            token("ㅅ", "FINAL", n / 3, n, minSupport)));
  }

  private JamoErrorStat token(
      String jamo, String position, int errors, int samples, int minSupport) {
    boolean enough = samples >= minSupport;
    return new JamoErrorStat(
        jamo,
        position,
        errors,
        samples,
        enough ? (double) errors / samples : null,
        enough ? "OK" : "INSUFFICIENT_DATA");
  }
}
