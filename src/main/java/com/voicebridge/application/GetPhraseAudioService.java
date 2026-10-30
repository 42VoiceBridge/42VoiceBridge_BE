package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.GetPhraseAudioUseCase;
import com.voicebridge.port.out.TtsAudioReadPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** local 프로파일 전용. 빈 등록은 LocalPhrasePlaybackConfig가 한다(파일을 읽는 TtsAudioReadPort가 local에만 있다). */
@RequiredArgsConstructor
public class GetPhraseAudioService implements GetPhraseAudioUseCase {
  private final PhraseAccess access;
  private final TtsAudioReadPort audio;

  @Override
  public byte[] getAudio(UUID userId, UUID phraseId) {
    var phrase = access.findUsablePhrase(userId, phraseId);
    if (!phrase.hasAudio()) {
      throw new CustomException(
          ErrorCode.INVALID_STATE_TRANSITION, "아직 음성이 없는 문구입니다. 먼저 말하기를 요청해 주세요.");
    }
    return audio.readAudio(phrase.getAudioStorageKey());
  }
}
