package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.out.PhraseRepositoryPort;
import com.voicebridge.port.out.TtsAudioReadPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPhraseAudioServiceTest {

  @Mock PhraseRepositoryPort phrases;
  @Mock TtsAudioReadPort audio;

  private final UUID me = UUID.randomUUID();

  private GetPhraseAudioService service() {
    return new GetPhraseAudioService(new PhraseAccess(phrases), audio);
  }

  @Test
  void 음성이_있는_내_문구는_파일을_읽는다() {
    Phrase mine = Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요.");
    String key = "recordings/" + UUID.randomUUID() + ".mp3";
    mine.attachAudio(key);
    when(phrases.findById(mine.getId())).thenReturn(Optional.of(mine));
    when(audio.readAudio(key)).thenReturn(new byte[] {7});

    assertThat(service().getAudio(me, mine.getId())).containsExactly(7);
  }

  @Test
  void 음성이_아직_없으면_409다() {
    Phrase mine = Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요.");
    when(phrases.findById(mine.getId())).thenReturn(Optional.of(mine));

    assertThatThrownBy(() -> service().getAudio(me, mine.getId()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    verifyNoInteractions(audio);
  }

  @Test
  void 다른_사람의_문구는_403이고_파일을_읽지_않는다() {
    Phrase theirs = Phrase.createForUser(UUID.randomUUID(), PhraseCategory.DAILY, "다른 사람 문구");
    theirs.attachAudio("recordings/" + UUID.randomUUID() + ".mp3");
    when(phrases.findById(theirs.getId())).thenReturn(Optional.of(theirs));

    assertThatThrownBy(() -> service().getAudio(me, theirs.getId()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN_ACCESS);
    verifyNoInteractions(audio);
  }
}
