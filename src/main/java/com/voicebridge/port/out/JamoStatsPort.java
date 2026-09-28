package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.JamoErrorStat;
import java.util.List;

/**
 * 정답·인식 결과 쌍을 AI에 넘겨 자모 단위 인식 오류 통계를 받는다(AI 계약 jamo-err-v1). AI는 사용자를 저장하지 않고 받은 쌍만으로 계산하므로, 어떤 쌍을
 * 모아 보낼지는 호출하는 쪽이 정한다.
 */
public interface JamoStatsPort {

  /**
   * pairs가 비어 있으면 AI가 거절(422)하므로 호출하기 전에 걸러야 한다. AI를 쓸 수 없으면
   * CustomException(AI_INFERENCE_UNAVAILABLE)을 던진다.
   */
  JamoStatsResult analyze(List<TextPair> pairs, int minSupport);

  /** answerText는 등록 문장처럼 정답이 확실한 텍스트여야 한다. 모델 출력을 정답 자리에 넣으면 모델이 틀린 것을 맞았다고 세게 된다(AI 계약 §3.5). */
  record TextPair(String answerText, String recognizedText) {}

  record JamoStatsResult(
      String metricVersion, int minSupport, int pairsUsed, List<JamoErrorStat> tokens) {}
}
