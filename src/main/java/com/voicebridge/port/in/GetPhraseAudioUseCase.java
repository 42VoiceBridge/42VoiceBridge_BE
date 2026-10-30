package com.voicebridge.port.in;

import java.util.UUID;

/** local 프로파일 전용 문구 음성 파일 조회. API 명세서 10.5절 참고. */
public interface GetPhraseAudioUseCase {
  byte[] getAudio(UUID userId, UUID phraseId);
}
