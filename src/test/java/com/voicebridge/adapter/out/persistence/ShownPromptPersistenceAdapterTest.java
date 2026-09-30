package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.recommendation.ShownPrompt;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ShownPromptPersistenceAdapter.class)
class ShownPromptPersistenceAdapterTest {

  @Autowired private ShownPromptPersistenceAdapter adapter;
  @Autowired private TestEntityManager entityManager;

  private ShownPrompt shown(UUID userId, String promptId) {
    return ShownPrompt.create(userId, promptId, "문장 " + promptId, "random", "prompt-random-v1", 7L);
  }

  @Test
  void 저장한_기록의_값이_그대로_복원된다() {
    UUID userId = UUID.randomUUID();
    ShownPrompt original = shown(userId, "02-03-0001");

    List<ShownPrompt> saved = adapter.saveAll(List.of(original));

    ShownPrompt restored = saved.get(0);
    assertThat(restored.getId()).isEqualTo(original.getId());
    assertThat(restored.getPromptId()).isEqualTo("02-03-0001");
    assertThat(restored.getText()).isEqualTo("문장 02-03-0001");
    assertThat(restored.getStrategyVersion()).isEqualTo("prompt-random-v1");
    assertThat(restored.getSeed()).isEqualTo(7L);
  }

  @Test
  void 이_사용자에게_보여준_문장_ID만_조회한다() {
    UUID userId = UUID.randomUUID();
    adapter.saveAll(List.of(shown(userId, "02-03-0001"), shown(userId, "06-01-0003")));
    adapter.saveAll(List.of(shown(UUID.randomUUID(), "02-04-0002")));
    entityManager.flush();
    entityManager.clear();

    assertThat(adapter.findPromptIdsByUserId(userId))
        .containsExactlyInAnyOrder("02-03-0001", "06-01-0003");
  }

  @Test
  void 보여준_문장이_없으면_빈_집합이다() {
    assertThat(adapter.findPromptIdsByUserId(UUID.randomUUID())).isEmpty();
  }
}
