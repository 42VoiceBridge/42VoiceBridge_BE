package com.voicebridge.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuggestionPolicyTest {

  @Test
  void 문구_규칙에_맞지_않는_후보는_버리고_앞뒤_공백은_지운다() {
    List<String> raw =
        Arrays.asList(
            "  물 좀 주세요.  ", "", "   ", null, "줄바꿈이\n있는 문장", "가".repeat(101), "물 한 잔 주세요.");

    assertThat(SuggestionPolicy.filter(raw)).containsExactly("물 좀 주세요.", "물 한 잔 주세요.");
  }

  @Test
  void 중복은_처음_나온_것만_남긴다() {
    assertThat(SuggestionPolicy.filter(List.of("물 좀 주세요.", " 물 좀 주세요.", "물 주세요.")))
        .containsExactly("물 좀 주세요.", "물 주세요.");
  }

  @Test
  void 후보는_최대_5개다() {
    List<String> raw = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      raw.add("문장 " + i);
    }

    assertThat(SuggestionPolicy.filter(raw))
        .hasSize(SuggestionPolicy.MAX_CANDIDATES)
        .containsExactly("문장 0", "문장 1", "문장 2", "문장 3", "문장 4");
  }

  @Test
  void 전부_걸러지거나_null이면_빈_목록이다() {
    assertThat(SuggestionPolicy.filter(List.of(" ", "\t"))).isEmpty();
    assertThat(SuggestionPolicy.filter(null)).isEmpty();
  }
}
