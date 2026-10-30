package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.domain.suggestion.SuggestionPolicy;
import com.voicebridge.port.in.SuggestPhrasesUseCase;
import com.voicebridge.port.out.CallRateLimitPort;
import com.voicebridge.port.out.LlmSuggestionPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 문장 추천. DB를 쓰지 않으므로 @Transactional을 두지 않는다(LLM 호출이 트랜잭션 안에 들어갈 일도 없다).
 *
 * <p>입력과 후보는 사용자가 쓴 내용이라 로그에 남기지 않는다. 결과 개수만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestPhrasesService implements SuggestPhrasesUseCase {

  static final String FEATURE = "suggestion";

  private final LlmSuggestionPort llmSuggestionPort;
  private final CallRateLimitPort callRateLimitPort;

  @Override
  public SuggestResult suggest(SuggestCommand command) {
    if (command.userId() == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    // 검증을 상한보다 먼저 한다. 잘못된 요청이 사용자의 호출 횟수를 깎지 않게.
    String text = Phrase.normalizeText(command.text());
    PhraseCategory category =
        command.category() == null ? null : PhraseCategory.from(command.category());

    if (!callRateLimitPort.tryAcquire(
        command.userId(),
        FEATURE,
        SuggestionPolicy.MAX_CALLS_PER_MINUTE,
        SuggestionPolicy.RATE_WINDOW)) {
      throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
    }

    List<String> candidates = SuggestionPolicy.filter(llmSuggestionPort.expand(text, category));
    if (candidates.isEmpty()) {
      log.warn("[문장 추천] 쓸 수 있는 후보가 없음");
      throw new CustomException(ErrorCode.SUGGESTION_UNAVAILABLE);
    }
    log.info("[문장 추천] 후보 {}개", candidates.size());
    return new SuggestResult(candidates);
  }
}
