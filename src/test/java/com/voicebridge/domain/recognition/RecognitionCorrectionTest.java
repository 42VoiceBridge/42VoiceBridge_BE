package com.voicebridge.domain.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecognitionCorrectionTest {

  @Test
  void 철자만_고친_보정본은_받아들인다() {
    assertThat(RecognitionCorrection.accept("애츨할 때 티브이 꺼 즈어", "외출할 때 티브이 꺼 줘"))
        .contains("외출할 때 티브이 꺼 줘");
  }

  @Test
  void 앞뒤_공백은_지우고_받아들인다() {
    assertThat(RecognitionCorrection.accept("물 좀 주세여", "  물 좀 주세요  ")).contains("물 좀 주세요");
  }

  @Test
  void 원문과_같아도_받아들인다() {
    assertThat(RecognitionCorrection.accept("물 좀 주세요", "물 좀 주세요")).contains("물 좀 주세요");
  }

  @Test
  void 뜻이_바뀔_만큼_다른_문장은_버린다() {
    assertThat(RecognitionCorrection.accept("물 좀 주세요", "오늘 병원 예약을 취소하고 싶습니다")).isEmpty();
  }

  @Test
  void 원문을_크게_늘린_문장은_버린다() {
    assertThat(RecognitionCorrection.accept("물", "물 좀 주시겠어요? 많이 목이 말라요")).isEmpty();
  }

  @Test
  void 빈_보정본이나_제어문자가_있으면_버린다() {
    assertThat(RecognitionCorrection.accept("물 좀 주세요", null)).isEmpty();
    assertThat(RecognitionCorrection.accept("물 좀 주세요", "   ")).isEmpty();
    assertThat(RecognitionCorrection.accept("물 좀 주세요", "물 좀\n주세요")).isEmpty();
  }

  @Test
  void 원문이_비었으면_보정하지_않는다() {
    // 무음으로 인식된 결과를 LLM이 문장으로 지어내지 못하게 한다.
    assertThat(RecognitionCorrection.accept("", "물 좀 주세요")).isEmpty();
    assertThat(RecognitionCorrection.accept("  ", "물 좀 주세요")).isEmpty();
    assertThat(RecognitionCorrection.accept(null, "물 좀 주세요")).isEmpty();
  }

  @Test
  void 편집_거리는_글자_단위로_센다() {
    assertThat(RecognitionCorrection.editDistance("주세여", "주세요")).isEqualTo(1);
    assertThat(RecognitionCorrection.editDistance("", "물")).isEqualTo(1);
    assertThat(RecognitionCorrection.editDistance("물", "물")).isZero();
  }
}
