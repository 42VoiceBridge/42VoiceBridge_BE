package com.voicebridge.domain.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TtsRequestTest {

  @Test
  void 생성하면_PENDING_상태이고_audioUrl은_null이다() {
    TtsRequest ttsRequest = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());

    assertThat(ttsRequest.getStatus()).isEqualTo(TtsRequestStatus.PENDING);
    assertThat(ttsRequest.getAudioUrl()).isNull();
    assertThat(ttsRequest.getCreatedAt()).isNotNull();
  }

  @Test
  void confirmationId가_없으면_생성할_수_없다() {
    assertThatThrownBy(() -> TtsRequest.create(null, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void idempotencyKey가_없으면_생성할_수_없다() {
    assertThatThrownBy(() -> TtsRequest.create(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void PENDING_상태에서_완료_처리하면_COMPLETED가_되고_audioUrl이_반영된다() {
    TtsRequest ttsRequest = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());

    ttsRequest.markCompleted("tts/abc.mp3");

    assertThat(ttsRequest.getStatus()).isEqualTo(TtsRequestStatus.COMPLETED);
    assertThat(ttsRequest.getAudioUrl()).isEqualTo("tts/abc.mp3");
  }

  @Test
  void PENDING이_아니면_완료_처리할_수_없다() {
    TtsRequest ttsRequest = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());
    ttsRequest.markCompleted("tts/abc.mp3");

    assertThatThrownBy(() -> ttsRequest.markCompleted("tts/other.mp3"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 완료_처리에는_audioUrl이_필요하다() {
    TtsRequest ttsRequest = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> ttsRequest.markCompleted(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void PENDING_상태에서_실패_처리하면_FAILED가_된다() {
    TtsRequest ttsRequest = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());

    ttsRequest.markFailed();

    assertThat(ttsRequest.getStatus()).isEqualTo(TtsRequestStatus.FAILED);
    assertThat(ttsRequest.getAudioUrl()).isNull();
  }

  @Test
  void PENDING이_아니면_실패_처리할_수_없다() {
    TtsRequest ttsRequest = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());
    ttsRequest.markFailed();

    assertThatThrownBy(ttsRequest::markFailed).isInstanceOf(IllegalStateException.class);
  }
}
