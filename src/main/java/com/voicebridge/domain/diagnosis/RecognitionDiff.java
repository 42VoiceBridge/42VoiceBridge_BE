package com.voicebridge.domain.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 정답 문장과 인식 결과를 글자 단위로 견주어 어긋난 지점을 찾는다(API 명세서 2.4절 diffHighlights).
 *
 * <p>인덱스를 나란히 비교하지 않고 편집 거리(edit distance)로 대응 관계를 먼저 정한다. 나란히 비교하면 글자 하나가 빠진 순간 뒤쪽 전부가 오인식으로
 * 표시되는데, 구음장애 발화는 글자 누락이 흔해서 화면이 거의 다 빨갛게 된다. 사용자에게 "거의 다 틀렸다"고 보이게 만드는 건 이 기능의 목적과 반대다.
 */
public final class RecognitionDiff {

  private RecognitionDiff() {}

  /**
   * 어긋난 지점 목록. position은 정답 문자열의 0-based 인덱스이며 공백을 포함해 센다 — 프론트가 화면에 뿌린 문자열에 그대로 인덱싱할 수 있어야 하기
   * 때문이다.
   *
   * @param expected 정답 글자. 인식에만 끼어든 글자라면 null
   * @param recognized 인식된 글자. 정답에 있었는데 빠졌다면 null
   */
  public record Highlight(int position, String expected, String recognized) {}

  public static List<Highlight> between(String answerText, String recognizedText) {
    if (answerText == null || answerText.isEmpty() || recognizedText == null) {
      return List.of();
    }
    // 아무것도 인식되지 않은 것은 "특정 글자가 틀렸다"가 아니라 "발화가 잡히지 않았다"는 다른 상황이다.
    // 그 사실은 recognizedText가 비어 있다는 것으로 이미 전달되므로 정답 전체를 오인식으로 늘어놓지 않는다.
    if (recognizedText.isEmpty()) {
      return List.of();
    }
    return backtrace(answerText, recognizedText, editDistances(answerText, recognizedText));
  }

  /**
   * d[i][j] = 정답 앞 i글자를 인식 결과 앞 j글자로 바꾸는 데 필요한 최소 편집 횟수. 한글 음절은 UTF-16에서 한 char에 담기므로 char 단위로 센다.
   */
  private static int[][] editDistances(String answer, String recognized) {
    int m = answer.length();
    int n = recognized.length();
    int[][] d = new int[m + 1][n + 1];
    for (int i = 0; i <= m; i++) {
      d[i][0] = i; // 인식 결과가 없으면 정답 글자를 모두 지워야 한다
    }
    for (int j = 0; j <= n; j++) {
      d[0][j] = j; // 정답이 없으면 인식 글자를 모두 끼워넣어야 한다
    }
    for (int i = 1; i <= m; i++) {
      for (int j = 1; j <= n; j++) {
        if (answer.charAt(i - 1) == recognized.charAt(j - 1)) {
          d[i][j] = d[i - 1][j - 1];
        } else {
          int substitute = d[i - 1][j - 1];
          int delete = d[i - 1][j];
          int insert = d[i][j - 1];
          d[i][j] = 1 + Math.min(substitute, Math.min(delete, insert));
        }
      }
    }
    return d;
  }

  /** 완성된 표를 끝에서 거꾸로 따라가며 어떤 편집이 쓰였는지 복원한다. */
  private static List<Highlight> backtrace(String answer, String recognized, int[][] d) {
    List<Highlight> highlights = new ArrayList<>();
    int i = answer.length();
    int j = recognized.length();
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && answer.charAt(i - 1) == recognized.charAt(j - 1)) {
        i--;
        j--;
      } else if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1) {
        add(highlights, i - 1, character(answer, i - 1), character(recognized, j - 1));
        i--;
        j--;
      } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
        add(highlights, i - 1, character(answer, i - 1), null); // 정답에 있던 글자가 빠졌다
        i--;
      } else {
        add(highlights, i, null, character(recognized, j - 1)); // 인식에만 글자가 끼어들었다
        j--;
      }
    }
    Collections.reverse(highlights);
    return highlights;
  }

  /**
   * 띄어쓰기만 어긋난 것은 제외한다. 띄어쓰기는 발화자의 발음이 아니라 인식 모델이 임의로 붙이는 부분이라 "오인식 구간"으로 보여줄 대상이 아니다. 한쪽이 실제 글자인
   * 대체는 그대로 남긴다.
   */
  private static void add(
      List<Highlight> highlights, int position, String expected, String recognized) {
    boolean whitespaceOnly =
        (expected == null || expected.isBlank()) && (recognized == null || recognized.isBlank());
    if (whitespaceOnly) {
      return;
    }
    highlights.add(new Highlight(position, expected, recognized));
  }

  private static String character(String text, int index) {
    return String.valueOf(text.charAt(index));
  }
}
