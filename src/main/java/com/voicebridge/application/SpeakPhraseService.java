package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.port.in.SpeakPhraseUseCase;
import com.voicebridge.port.out.AudioPlaybackUrlPort;
import com.voicebridge.port.out.PhraseRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TtsEnginePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 문구 말하기. 처음 누르면 클로바로 합성해 저장소에 올리고 문구에 음성 key를 붙인다. 다음부터는 저장한 음성의 재생 URL만 만든다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다. 클로바 합성(1~2초)과 업로드가 DB 커넥션과 트랜잭션을 붙잡지 않게 하기 위해서다. 조회와 저장은 어댑터가 각자
 * 짧은 트랜잭션으로 끝낸다.
 *
 * <p>문구 텍스트는 사용자가 쓴 내용이라 로그에 남기지 않는다. 로그에는 phraseId만 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakPhraseService implements SpeakPhraseUseCase {

  private static final String AUDIO_FILE_NAME = "phrase.mp3";

  private final PhraseAccess access;
  private final PhraseRepositoryPort phraseRepositoryPort;
  private final TtsEnginePort ttsEnginePort;
  private final StoragePort storagePort;
  private final AudioPlaybackUrlPort playbackUrls;

  @Override
  public SpeakResult speak(UUID userId, UUID phraseId) {
    Phrase phrase = access.findUsablePhrase(userId, phraseId);
    if (!phrase.hasAudio()) {
      phrase = synthesizeAndCache(phrase);
    }
    return new SpeakResult(
        phrase.getId(), playbackUrls.createPhraseUrl(phrase.getAudioStorageKey(), phrase.getId()));
  }

  private Phrase synthesizeAndCache(Phrase phrase) {
    byte[] audio;
    try {
      audio = ttsEnginePort.synthesize(phrase.getText());
    } catch (RuntimeException e) {
      // 실패하면 아무것도 저장하지 않는다. 다음에 누르면 다시 합성을 시도한다.
      // 예외 메시지에 요청 내용이 섞일 수 있어 예외 종류만 남긴다.
      log.warn(
          "[문구 말하기] 음성 합성 실패 phraseId={} cause={}", phrase.getId(), e.getClass().getSimpleName());
      throw new CustomException(ErrorCode.TTS_UNAVAILABLE);
    }
    String storageKey = storagePort.upload(audio, AUDIO_FILE_NAME);

    // 합성하는 동안 다른 요청이 먼저 음성을 붙였거나 문구가 삭제됐을 수 있어 최신 상태를 다시 읽는다.
    Phrase latest = phraseRepositoryPort.findById(phrase.getId()).orElse(null);
    if (latest == null) {
      discard(storageKey, phrase.getId());
      throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    if (!latest.attachAudio(storageKey)) {
      // 먼저 붙은 음성을 쓰고 방금 올린 것은 지운다.
      discard(storageKey, phrase.getId());
      return latest;
    }
    return phraseRepositoryPort.save(latest);
  }

  // 정리 실패는 응답을 바꾸지 않는다. 남는 파일은 재생에 쓰이지 않는 고아 파일일 뿐이다.
  private void discard(String storageKey, UUID phraseId) {
    try {
      storagePort.delete(storageKey);
    } catch (RuntimeException e) {
      log.warn("[문구 말하기] 쓰지 않는 음성 삭제 실패 phraseId={}", phraseId);
    }
  }
}
