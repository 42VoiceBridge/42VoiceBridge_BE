package com.voicebridge.domain.recommendation;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 추천 응답으로 사용자에게 제안한 문장 한 건의 기록(AI 계약 §4 prompt_shown). 이름은 AI 계약의 엔티티명과 맞췄지만, 화면에 실제로 표시됐는지나 녹음했는지는
 * 뜻하지 않는다. 그래서 학습 자격을 판단하는 근거로 쓰지 않는다.
 *
 * <p>어떤 전략·버전·seed·문장 풀로 뽑혔는지 함께 남겨야 나중에 어떤 추천 방식이 인식 개선에 도움이 됐는지 비교할 수 있다. 제안한 문장의 원문을 확인하는 근거이기도
 * 하다.
 */
public class ShownPrompt {

  private final UUID id;
  private final UUID userId;
  private final String promptId;
  private final String text;
  private final String strategy;
  private final String strategyVersion;
  private final long seed;
  private final String poolVersion;
  private final String poolSha256;
  private final LocalDateTime shownAt;

  private ShownPrompt(
      UUID id,
      UUID userId,
      String promptId,
      String text,
      String strategy,
      String strategyVersion,
      long seed,
      String poolVersion,
      String poolSha256,
      LocalDateTime shownAt) {
    this.id = id;
    this.userId = userId;
    this.promptId = promptId;
    this.text = text;
    this.strategy = strategy;
    this.strategyVersion = strategyVersion;
    this.seed = seed;
    this.poolVersion = poolVersion;
    this.poolSha256 = poolSha256;
    this.shownAt = shownAt;
  }

  public static ShownPrompt create(
      UUID userId,
      String promptId,
      String text,
      String strategy,
      String strategyVersion,
      long seed,
      String poolVersion,
      String poolSha256) {
    if (userId == null || isBlank(promptId) || isBlank(text)) {
      throw new IllegalArgumentException("추천 문장 기록에는 사용자, 문장 ID, 원문이 필요합니다.");
    }
    // 전략과 버전이 빠진 기록은 어떤 방식으로 뽑힌 문장인지 알 수 없어 비교에 쓸 수 없다
    if (isBlank(strategy) || isBlank(strategyVersion)) {
      throw new IllegalArgumentException("추천 문장 기록에는 선택 전략과 버전이 필요합니다.");
    }
    // 문장 풀이 바뀌면 같은 전략·seed로도 다른 문장이 나와서, 풀을 모르면 이 추천을 재현할 수 없다
    if (isBlank(poolVersion) || isBlank(poolSha256)) {
      throw new IllegalArgumentException("추천 문장 기록에는 문장 풀 버전과 해시가 필요합니다.");
    }
    return new ShownPrompt(
        UUID.randomUUID(),
        userId,
        promptId,
        text,
        strategy,
        strategyVersion,
        seed,
        poolVersion,
        poolSha256,
        LocalDateTime.now());
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. 2026-10-03 이전 기록은 문장 풀 버전과 해시가 없어 null로 복원된다. */
  public static ShownPrompt reconstitute(
      UUID id,
      UUID userId,
      String promptId,
      String text,
      String strategy,
      String strategyVersion,
      long seed,
      String poolVersion,
      String poolSha256,
      LocalDateTime shownAt) {
    return new ShownPrompt(
        id,
        userId,
        promptId,
        text,
        strategy,
        strategyVersion,
        seed,
        poolVersion,
        poolSha256,
        shownAt);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getPromptId() {
    return promptId;
  }

  public String getText() {
    return text;
  }

  public String getStrategy() {
    return strategy;
  }

  public String getStrategyVersion() {
    return strategyVersion;
  }

  public long getSeed() {
    return seed;
  }

  public String getPoolVersion() {
    return poolVersion;
  }

  public String getPoolSha256() {
    return poolSha256;
  }

  public LocalDateTime getShownAt() {
    return shownAt;
  }
}
