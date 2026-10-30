package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 문구 말하기 계약. API 명세서 10.4절 참고. 처음 누르면 합성해 저장하고, 다음부터는 저장한 음성을 다시 쓴다. 합성 서버가 실패하면 503이고 아무것도 저장하지
 * 않는다.
 */
public interface SpeakPhraseUseCase {

  SpeakResult speak(UUID userId, UUID phraseId);

  record SpeakResult(UUID phraseId, String audioUrl) {}
}
