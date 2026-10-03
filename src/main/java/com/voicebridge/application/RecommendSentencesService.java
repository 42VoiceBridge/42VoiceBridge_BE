package com.voicebridge.application;

import com.voicebridge.domain.recommendation.RecommendationCount;
import com.voicebridge.domain.recommendation.ShownPrompt;
import com.voicebridge.port.in.RecommendSentencesUseCase;
import com.voicebridge.port.out.EnrollmentPromptPort;
import com.voicebridge.port.out.EnrollmentPromptPort.PromptBatch;
import com.voicebridge.port.out.ShownPromptRepositoryPort;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// @Transactional을 걸지 않는다. AI의 HTTP 응답을 기다리는 동안 DB 연결을 붙잡고 있지 않도록 조회와 저장은 각 어댑터가 따로 처리한다.
@Service
@RequiredArgsConstructor
public class RecommendSentencesService implements RecommendSentencesUseCase {

  private final ShownPromptRepositoryPort shownPromptRepositoryPort;
  private final EnrollmentPromptPort enrollmentPromptPort;

  @Override
  public RecommendationResult recommend(UUID userId, Integer count) {
    int resolvedCount = RecommendationCount.resolve(count);
    Set<String> alreadyShown = shownPromptRepositoryPort.findPromptIdsByUserId(userId);

    // AI는 seed와 제외 목록이 같으면 같은 문장을 돌려준다. 호출마다 새로 뽑지 않으면 처음 추천받는 사용자들이 모두 같은 문장을 받는다.
    long seed = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    PromptBatch batch = enrollmentPromptPort.nextPrompts(userId, resolvedCount, seed, alreadyShown);

    List<ShownPrompt> shown =
        batch.prompts().stream()
            .map(
                prompt ->
                    ShownPrompt.create(
                        userId,
                        prompt.promptId(),
                        prompt.text(),
                        batch.strategy(),
                        batch.strategyVersion(),
                        batch.seed(),
                        batch.poolVersion(),
                        batch.poolSha256()))
            .toList();
    // 문장 풀을 모두 본 사용자는 빈 목록을 받는다
    if (!shown.isEmpty()) {
      shownPromptRepositoryPort.saveAll(shown);
    }

    return new RecommendationResult(
        shown.stream()
            .map(
                prompt ->
                    new RecommendedSentence(prompt.getId(), prompt.getPromptId(), prompt.getText()))
            .toList());
  }
}
