package com.voicebridge.port.out;

/** 실제 음성 합성 엔진 호출 계약. 구현체는 {@code adapter/out/tts}에 둔다. */
public interface TtsEnginePort {

  /** 텍스트를 합성해 오디오 바이너리(mp3)를 반환한다. 실패 시(4xx/5xx, 네트워크 오류) 런타임 예외를 던진다. */
  byte[] synthesize(String text);
}
