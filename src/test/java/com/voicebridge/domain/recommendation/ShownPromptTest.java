package com.voicebridge.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShownPromptTest {

  private final UUID userId = UUID.randomUUID();

  @Test
  void 보여준_문장을_전략_버전_seed와_함께_기록한다() {
    ShownPrompt shown =
        ShownPrompt.create(userId, "02-03-0001", "식당이 어디예요?", "random", "prompt-random-v1", 42L);

    assertThat(shown.getId()).isNotNull();
    assertThat(shown.getStrategy()).isEqualTo("random");
    assertThat(shown.getStrategyVersion()).isEqualTo("prompt-random-v1");
    assertThat(shown.getSeed()).isEqualTo(42L);
    assertThat(shown.getShownAt()).isNotNull();
  }

  @Test
  void 전략이나_버전이_없으면_비교에_쓸_수_없어_기록하지_않는다() {
    assertThatThrownBy(
            () -> ShownPrompt.create(userId, "02-03-0001", "식당이 어디예요?", "random", null, 42L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ShownPrompt.create(userId, "02-03-0001", "식당이 어디예요?", " ", "prompt-random-v1", 42L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 원문이_없으면_사용자가_무엇을_읽었는지_알_수_없어_기록하지_않는다() {
    assertThatThrownBy(
            () -> ShownPrompt.create(userId, "02-03-0001", "", "random", "prompt-random-v1", 42L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
