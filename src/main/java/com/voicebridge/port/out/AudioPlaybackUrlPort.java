package com.voicebridge.port.out;

import java.util.UUID;

public interface AudioPlaybackUrlPort {
  String createUrl(String storageKey, UUID ttsId);

  /** 문구 음성의 재생 URL. 저장 key 형식은 TTS 음성과 같고, local은 문구 음성 조회 주소, 운영은 S3 임시 URL을 만든다. */
  String createPhraseUrl(String storageKey, UUID phraseId);
}
