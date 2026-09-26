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
}
