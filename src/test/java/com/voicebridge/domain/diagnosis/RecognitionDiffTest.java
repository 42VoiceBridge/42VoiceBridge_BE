package com.voicebridge.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.diagnosis.RecognitionDiff.Highlight;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecognitionDiffTest {

  private static final String ANSWER = "오늘 날씨가 좋습니다";

  @Test
  void 한_글자가_바뀌면_그_위치만_표시한다() {
    List<Highlight> highlights = RecognitionDiff.between(ANSWER, "오늘 날씨가 조습니다");

    // position은 공백을 포함해 센다(오0 늘1 _2 날3 씨4 가5 _6 좋7)
    assertThat(highlights).containsExactly(new Highlight(7, "좋", "조"));
  }

  @Test
  void 글자가_빠져도_뒤쪽_글자는_오인식으로_표시하지_않는다() {
    List<Highlight> highlights = RecognitionDiff.between(ANSWER, "오늘 씨가 좋습니다");

    // 인덱스를 나란히 비교했다면 '날' 뒤의 글자가 전부 어긋난 것으로 나온다
    assertThat(highlights).containsExactly(new Highlight(3, "날", null));
  }

  @Test
  void 인식에만_끼어든_글자는_expected가_null이다() {
    List<Highlight> highlights = RecognitionDiff.between(ANSWER, "오늘 날씨가 좋습니니다");

    assertThat(highlights).hasSize(1);
    assertThat(highlights.get(0).expected()).isNull();
    assertThat(highlights.get(0).recognized()).isEqualTo("니");
  }

  @Test
  void 완전히_일치하면_빈_목록이다() {
    assertThat(RecognitionDiff.between(ANSWER, ANSWER)).isEmpty();
  }

  @Test
  void 무음이라_인식_결과가_비어있으면_빈_목록이다() {
    // 정답 전체를 누락으로 늘어놓으면 화면이 전부 빨갛게 된다. 발화가 안 잡힌 사실은 recognizedText로 이미 전달된다.
    assertThat(RecognitionDiff.between(ANSWER, "")).isEmpty();
  }

  @Test
  void 띄어쓰기만_다르면_빈_목록이다() {
    assertThat(RecognitionDiff.between(ANSWER, "오늘날씨가 좋습니다")).isEmpty();
    assertThat(RecognitionDiff.between(ANSWER, "오늘 날씨가 좋 습니다")).isEmpty();
  }

  @Test
  void 공백_자리에_실제_글자가_들어오면_표시한다() {
    List<Highlight> highlights = RecognitionDiff.between("가 나", "가다나");

    assertThat(highlights).containsExactly(new Highlight(1, " ", "다"));
  }

  @Test
  void 인식이_끝나지_않아_결과가_null이면_빈_목록이다() {
    assertThat(RecognitionDiff.between(ANSWER, null)).isEmpty();
  }

  @Test
  void 정답_문장을_찾지_못해_null이면_빈_목록이다() {
    assertThat(RecognitionDiff.between(null, "오늘 날씨가 조습니다")).isEmpty();
  }
}
