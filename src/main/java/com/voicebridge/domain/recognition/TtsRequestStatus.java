package com.voicebridge.domain.recognition;

public enum TtsRequestStatus {
  PENDING,
  COMPLETED,
  // 실제 음성 합성 엔진이 아직 미정이라 지금은 어디서도 실패로 전이시키지 않는다 — 엔진 결정 후 사용.
  FAILED
}
