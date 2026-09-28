package com.voicebridge.domain.diagnosis;

/**
 * 자모 하나의 인식 오류 통계. position은 INITIAL/MEDIAL/FINAL(초성/중성/종성), status는 OK/INSUFFICIENT_DATA다.
 *
 * @param errorRate 표본이 기준(minSupport)보다 적으면 null. 0.0으로 채우면 "충분히 측정했고 한 번도 안 틀렸다"와 구분되지 않는다.
 */
public record JamoErrorStat(
    String token, String position, int errors, int sampleCount, Double errorRate, String status) {}
