package com.voicebridge.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecommendationCountTest {

  @Test
  void 개수를_정하지_않으면_기본_10개다() {
    assertThat(RecommendationCount.resolve(null)).isEqualTo(10);
  }

  @Test
  void 경계값인_1개와_50개는_허용한다() {
    assertThat(RecommendationCount.resolve(1)).isEqualTo(1);
    assertThat(RecommendationCount.resolve(50)).isEqualTo(50);
  }

  @Test
  void 범위를_벗어나면_거절한다() {
    assertThatThrownBy(() -> RecommendationCount.resolve(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RecommendationCount.resolve(51))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
