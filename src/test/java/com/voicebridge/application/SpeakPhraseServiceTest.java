package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.out.AudioPlaybackUrlPort;
import com.voicebridge.port.out.PhraseRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TtsEnginePort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 합성하는 사이에 다른 요청이 끼어드는 경우만 여기서 본다. HTTP 수준의 정상 흐름(1회 합성, 503, 권한)은 PhraseIntegrationTest가 실제 DB로
 * 확인한다. 두 요청이 겹치는 순간은 통합 테스트로 재현하기 어려워 저장소 응답을 정해 둔다.
 */
@ExtendWith(MockitoExtension.class)
class SpeakPhraseServiceTest {

  @Mock PhraseRepositoryPort phrases;
  @Mock TtsEnginePort ttsEngine;
  @Mock StoragePort storage;
  @Mock AudioPlaybackUrlPort playbackUrls;

  private SpeakPhraseService service;
  private final UUID userId = UUID.randomUUID();
  private final Phrase phrase = Phrase.createDefault(PhraseCategory.HOSPITAL, "안녕하세요.");
  private final String winnerKey = "recordings/" + UUID.randomUUID() + ".mp3";
  private final String myKey = "recordings/" + UUID.randomUUID() + ".mp3";

  @BeforeEach
  void setUp() {
    service =
        new SpeakPhraseService(
            new PhraseAccess(phrases), phrases, ttsEngine, storage, playbackUrls);
    when(ttsEngine.synthesize("안녕하세요.")).thenReturn(new byte[] {1});
    when(storage.upload(any(), anyString())).thenReturn(myKey);
  }

  private Phrase copyWithAudio(String key) {
    return Phrase.reconstitute(
        phrase.getId(),
        phrase.getOwnerId(),
        phrase.getCategory(),
        phrase.getText(),
        key,
        phrase.getCreatedAt());
  }

  @Test
  void 합성하는_사이_다른_요청이_먼저_음성을_붙였으면_그_음성을_쓰고_방금_올린_것은_지운다() {
    // 처음 읽을 때는 음성이 없고, 합성 후 다시 읽으면 다른 요청이 붙인 음성이 있다
    when(phrases.findById(phrase.getId()))
        .thenReturn(Optional.of(phrase), Optional.of(copyWithAudio(winnerKey)));
    when(playbackUrls.createPhraseUrl(winnerKey, phrase.getId())).thenReturn("https://winner");

    var result = service.speak(userId, phrase.getId());

    assertThat(result.audioUrl()).isEqualTo("https://winner");
    verify(storage).delete(myKey);
    verify(phrases, never()).save(any());
  }

  @Test
  void 지우기에_실패해도_응답은_그대로다() {
    when(phrases.findById(phrase.getId()))
        .thenReturn(Optional.of(phrase), Optional.of(copyWithAudio(winnerKey)));
    when(playbackUrls.createPhraseUrl(winnerKey, phrase.getId())).thenReturn("https://winner");
    doThrow(new RuntimeException("s3 down")).when(storage).delete(myKey);

    assertThat(service.speak(userId, phrase.getId()).audioUrl()).isEqualTo("https://winner");
  }

  @Test
  void 합성하는_사이_문구가_삭제됐으면_404이고_올린_음성은_지운다() {
    when(phrases.findById(phrase.getId())).thenReturn(Optional.of(phrase), Optional.empty());

    assertThatThrownBy(() -> service.speak(userId, phrase.getId()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    verify(storage).delete(myKey);
    verify(phrases, never()).save(any());
  }
}
