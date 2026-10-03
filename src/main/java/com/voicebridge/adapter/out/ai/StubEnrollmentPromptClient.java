package com.voicebridge.adapter.out.ai;

import com.voicebridge.port.out.EnrollmentPromptPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 로컬 개발 전용. 실제 AI 문장 풀 대신 작은 고정 목록에서 고른다. 제외 목록과 seed는 실제처럼 지켜야, 추천할 때마다 새 문장이 나오고
// 같은 문장이 반복되지 않는 흐름을 로컬에서 확인할 수 있다.
@Slf4j
@Component
@Profile("local")
public class StubEnrollmentPromptClient implements EnrollmentPromptPort {

  // 버전을 실제와 다르게 두어, 로컬에서 쌓인 기록이 실제 AI 추천으로 오인되지 않게 한다
  private static final String STRATEGY_VERSION = "stub-random-v1";
  private static final String POOL_VERSION = "stub-pool-v1";
  private static final String POOL_SHA256 = "stub-pool-sha256";

  private static final List<Prompt> POOL =
      List.of(
          new Prompt("stub-001", "식당이 어디예요?"),
          new Prompt("stub-002", "서울역으로 가주세요."),
          new Prompt("stub-003", "물 좀 주세요."),
          new Prompt("stub-004", "지금 몇 시예요?"),
          new Prompt("stub-005", "화장실이 어디에 있나요?"),
          new Prompt("stub-006", "천천히 말씀해 주세요."),
          new Prompt("stub-007", "버스 정류장이 가까워요?"),
          new Prompt("stub-008", "약국에 가고 싶어요."),
          new Prompt("stub-009", "오늘은 날씨가 춥네요."),
          new Prompt("stub-010", "도와주셔서 감사합니다."),
          new Prompt("stub-011", "전화번호를 알려 주세요."),
          new Prompt("stub-012", "창문 좀 열어 주세요."));

  @Override
  public PromptBatch nextPrompts(
      UUID userId, int count, long seed, Collection<String> excludePromptIds) {
    log.info("[스텁 추천 문장] 실제 AI를 호출하지 않는다. count={} seed={}", count, seed);
    List<Prompt> candidates = new ArrayList<>();
    for (Prompt prompt : POOL) {
      if (!excludePromptIds.contains(prompt.promptId())) {
        candidates.add(prompt);
      }
    }
    Collections.shuffle(candidates, new Random(seed));
    return new PromptBatch(
        "random",
        STRATEGY_VERSION,
        seed,
        POOL_VERSION,
        POOL_SHA256,
        List.copyOf(candidates.subList(0, Math.min(count, candidates.size()))));
  }
}
