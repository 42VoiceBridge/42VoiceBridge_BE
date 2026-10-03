package com.voicebridge.port.out;

public interface TtsAudioReadPort {
  byte[] readAudio(String storageKey);
}
