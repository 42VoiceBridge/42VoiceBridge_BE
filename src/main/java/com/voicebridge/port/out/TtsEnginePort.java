package com.voicebridge.port.out;

/**
 * 실제 음성 합성 엔진 호출 계약. 엔진(앱 OS TTS vs 외부 TTS API, API 명세서 §10-4)이 아직 미정이라 이번 스켈레톤에는 구현체가 없다 —
 * AiInferenceClient를 PoC 전까지 스텁으로 두었던 것과 같은 원칙. 엔진이 결정되면 메서드를 채우고 구현체를 추가한다.
 *
 * <p>결정 전까지는 아무 곳에도 주입하지 않는다.
 */
public interface TtsEnginePort {
  // TODO: 엔진 미정. 결정되면 synthesize(text) 같은 메서드와 adapter/out/tts 구현체를 추가한다.
}
