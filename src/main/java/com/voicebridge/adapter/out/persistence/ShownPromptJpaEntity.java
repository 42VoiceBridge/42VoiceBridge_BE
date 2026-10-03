package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 조회는 "이 사용자에게 제안한 문장 ID"뿐이라 user_id에 인덱스를 둔다.
// 같은 사용자의 추천이 동시에 두 번 요청되면 같은 문장이 두 번 기록될 수 있어 (user_id, prompt_id)에 유일 제약을 걸지 않는다.
// 제안 이력이라 중복이 생겨도 해가 없고, 제약을 걸면 그 경우 추천 요청 자체가 실패한다.
@Entity
@Table(
    name = "shown_prompts",
    indexes = @Index(name = "idx_shown_prompts_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShownPromptJpaEntity {

  @Id private UUID id;

  private UUID userId;

  private String promptId;

  @Column(length = 500)
  private String text;

  private String strategy;

  private String strategyVersion;

  private long seed;

  private String poolVersion;

  private String poolSha256;

  private LocalDateTime shownAt;

  @Builder
  private ShownPromptJpaEntity(
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
}
