package com.voicebridge.adapter.out.storage;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;

// TTS 확인 문장 음성과 문구 음성이 같은 key 형식(recordings/{uuid}.mp3)을 쓴다.
final class TtsAudioKeys {
  private TtsAudioKeys() {}

  static boolean matches(String key) {
    return key != null
        && key.matches(
            "recordings/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mp3");
  }

  static void validate(String key) {
    if (!matches(key)) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 오디오 저장 경로가 올바르지 않습니다.");
    }
  }
}
