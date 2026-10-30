package com.voicebridge.port.out;

import com.voicebridge.domain.phrase.PhraseCategory;
import java.util.List;

/**
 * 문장 확장·문구 추천(LLM) 계약. 구현체는 {@code adapter/out/llm}에 둔다. 외부 SDK 타입을 이 시그니처에 노출하지 않는다.
 *
 * <p>실패(키 없음, 거절, 형식 오류, 타임아웃, 4xx/5xx)는 모두 CustomException(SUGGESTION_UNAVAILABLE)으로 던진다.
 */
public interface LlmSuggestionPort {

  /**
   * 짧은 발화("물")나 상황 키워드("병원 접수")를 받아 사용자가 직접 말할 법한 문장 후보를 돌려준다. category는 null일 수 있다. 돌려주는 후보는 거르기 전
   * 원본이다(거르기는 SuggestionPolicy).
   */
  List<String> expand(String text, PhraseCategory category);

  /**
   * 대화 모드(부록 C): 상대가 한 말(받아쓴 텍스트)에 사용자가 대답할 법한 문장 후보를 돌려준다. heardText는 신뢰할 수 없는 입력이다. 돌려주는 후보는 거르기
   * 전 원본이다.
   */
  List<String> replyTo(String heardText);
}
