package com.voicebridge.adapter.out.storage;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;

final class TtsAudioKeys {
  private TtsAudioKeys() {}

  static void validate(String key) {
    if (key == null
        || !key.matches(
            "recordings/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mp3")) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 오디오 저장 경로가 올바르지 않습니다.");
    }
  }
}
