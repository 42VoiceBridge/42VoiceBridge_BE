package com.voicebridge.adapter.out.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.conversation.ConversationTurn;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 Redis로 저장·복원과 만료를 확인한다. 턴마다 새 UUID라 테스트끼리 키가 섞이지 않는다. */
@Testcontainers
@SpringBootTest
class RedisConversationTurnStoreAdapterTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired RedisConversationTurnStoreAdapter store;
  @Autowired StringRedisTemplate redisTemplate;

  @Test
  void 저장한_턴을_그대로_읽는다() {
    ConversationTurn turn =
        ConversationTurn.create(UUID.randomUUID(), "식사하셨어요?", List.of("네.", "아니요."));

    store.save(turn, Duration.ofMinutes(10));
    ConversationTurn found = store.findById(turn.getId()).orElseThrow();

    assertThat(found.getId()).isEqualTo(turn.getId());
    assertThat(found.getOwnerId()).isEqualTo(turn.getOwnerId());
    assertThat(found.getHeardText()).isEqualTo("식사하셨어요?");
    assertThat(found.getCandidates()).isEqualTo(turn.getCandidates());
    assertThat(found.getCreatedAt()).isEqualTo(turn.getCreatedAt());
  }

  @Test
  void 없는_턴은_빈_값이다() {
    assertThat(store.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void 만료가_걸리고_지나면_읽을_수_없다() throws InterruptedException {
    ConversationTurn turn = ConversationTurn.create(UUID.randomUUID(), "식사하셨어요?", List.of());

    store.save(turn, Duration.ofMillis(500));
    assertThat(redisTemplate.getExpire("conversation:turn:" + turn.getId())).isNotNegative();
    assertThat(store.findById(turn.getId())).isPresent();

    Thread.sleep(700);

    assertThat(store.findById(turn.getId())).isEmpty();
  }

  @Test
  void 키에는_턴_ID만_들어간다() {
    ConversationTurn turn = ConversationTurn.create(UUID.randomUUID(), "식사하셨어요?", List.of());

    store.save(turn, Duration.ofMinutes(1));

    assertThat(redisTemplate.keys("conversation:turn:" + turn.getId()))
        .containsExactly("conversation:turn:" + turn.getId());
  }
}
