package com.voicebridge.adapter.out.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StubSpeechToTextAdapterTest {

  private final StubSpeechToTextAdapter adapter = new StubSpeechToTextAdapter();

  @Test
  void 음성이_있으면_고정_문장을_돌려준다() {
    assertThat(adapter.transcribe(new byte[] {1, 2, 3}))
        .isEqualTo(StubSpeechToTextAdapter.STUB_TEXT)
        .isNotBlank();
  }

  @Test
  void 빈_음성은_실제_어댑터처럼_거절한다() {
    assertThatThrownBy(() -> adapter.transcribe(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.transcribe(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
