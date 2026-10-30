package com.voicebridge.port.out;

/**
 * 외부 음성인식(STT). 대화 모드(부록 C)에서 상대의 말을 받아쓸 때 쓴다. 사용자 본인의 말은 기존 인식 흐름({@link AiInferenceClient})이 맡는다.
 */
public interface SpeechToTextPort {

  /**
   * {@link AudioNormalizationPort}로 정규화한 WAV(16kHz mono)를 받아 인식한 텍스트를 돌려준다. 인식 결과가 비었거나 외부 호출이 실패하면
   * CustomException(STT_UNAVAILABLE)을 던진다.
   */
  String transcribe(byte[] wavBytes);
}
