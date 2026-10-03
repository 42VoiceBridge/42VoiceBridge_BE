package com.voicebridge.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShownPromptTest {

  private static final String POOL = "script-pool-v1";
  private static final String POOL_SHA = "0123456789abcdef";

  private final UUID userId = UUID.randomUUID();

  @Test
  void 보여준_문장을_전략_버전_seed_문장_풀과_함께_기록한다() {
    ShownPrompt shown =
        ShownPrompt.create(
            userId, "02-03-0001", "식당이 어디예요?", "random", "prompt-random-v1", 42L, POOL, POOL_SHA);

    assertThat(shown.getId()).isNotNull();
    assertThat(shown.getStrategy()).isEqualTo("random");
    assertThat(shown.getStrategyVersion()).isEqualTo("prompt-random-v1");
    assertThat(shown.getSeed()).isEqualTo(42L);
    assertThat(shown.getPoolVersion()).isEqualTo(POOL);
    assertThat(shown.getPoolSha256()).isEqualTo(POOL_SHA);
    assertThat(shown.getShownAt()).isNotNull();
  }

  @Test
  void 문장_풀_버전이나_해시가_없으면_재현할_수_없어_기록하지_않는다() {
    assertThatThrownBy(
            () ->
                ShownPrompt.create(
                    userId,
                    "02-03-0001",
                    "식당이 어디예요?",
                    "random",
                    "prompt-random-v1",
                    42L,
                    null,
                    POOL_SHA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ShownPrompt.create(
                    userId,
                    "02-03-0001",
                    "식당이 어디예요?",
                    "random",
                    "prompt-random-v1",
                    42L,
                    POOL,
                    " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 전략이나_버전이_없으면_비교에_쓸_수_없어_기록하지_않는다() {
    assertThatThrownBy(
            () ->
                ShownPrompt.create(
                    userId, "02-03-0001", "식당이 어디예요?", "random", null, 42L, POOL, POOL_SHA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ShownPrompt.create(
                    userId,
                    "02-03-0001",
                    "식당이 어디예요?",
                    " ",
                    "prompt-random-v1",
                    42L,
                    POOL,
                    POOL_SHA))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 원문이_없으면_사용자가_무엇을_읽었는지_알_수_없어_기록하지_않는다() {
    assertThatThrownBy(
            () ->
                ShownPrompt.create(
                    userId, "02-03-0001", "", "random", "prompt-random-v1", 42L, POOL, POOL_SHA))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
