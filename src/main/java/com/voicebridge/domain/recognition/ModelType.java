package com.voicebridge.domain.recognition;

/** 인식에 사용된 모델 종류. port.out.AiInferenceClient도 이 값을 그대로 참조한다(SSOT) — 두 곳에서 따로 정의하지 않는다. */
public enum ModelType {
  BASE_ADAPTED,
  PERSONALIZED
}
