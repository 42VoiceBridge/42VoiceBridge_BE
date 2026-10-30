package com.voicebridge.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

/** 실제 Redis로 카운터와 만료를 확인한다. 테스트마다 새 사용자 UUID를 써서 키가 섞이지 않는다. */
@Testcontainers
@SpringBootTest
class RedisCallRateLimitAdapterTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired RedisCallRateLimitAdapter limiter;
  @Autowired StringRedisTemplate redisTemplate;

  @Test
  void 상한까지는_허용하고_넘으면_거부한다() {
    UUID userId = UUID.randomUUID();

    for (int i = 0; i < 10; i++) {
      assertThat(limiter.tryAcquire(userId, "suggestion", 10, Duration.ofMinutes(1))).isTrue();
    }
    assertThat(limiter.tryAcquire(userId, "suggestion", 10, Duration.ofMinutes(1))).isFalse();
  }

  @Test
  void 사용자와_기능마다_따로_센다() {
    UUID me = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    limiter.tryAcquire(me, "suggestion", 1, Duration.ofMinutes(1));

    assertThat(limiter.tryAcquire(me, "suggestion", 1, Duration.ofMinutes(1))).isFalse();
    assertThat(limiter.tryAcquire(other, "suggestion", 1, Duration.ofMinutes(1))).isTrue();
    assertThat(limiter.tryAcquire(me, "conversation", 1, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void 첫_호출에_만료가_걸리고_창이_지나면_다시_허용한다() throws InterruptedException {
    UUID userId = UUID.randomUUID();
    String key = "ratelimit:suggestion:" + userId;

    assertThat(limiter.tryAcquire(userId, "suggestion", 1, Duration.ofMillis(500))).isTrue();
    assertThat(redisTemplate.getExpire(key)).isNotNegative();
    assertThat(limiter.tryAcquire(userId, "suggestion", 1, Duration.ofMillis(500))).isFalse();

    Thread.sleep(700);

    assertThat(limiter.tryAcquire(userId, "suggestion", 1, Duration.ofMillis(500))).isTrue();
  }

  @Test
  void 키에는_입력_텍스트가_들어가지_않는다() {
    UUID userId = UUID.randomUUID();
    limiter.tryAcquire(userId, "suggestion", 10, Duration.ofMinutes(1));

    assertThat(redisTemplate.keys("ratelimit:*" + userId))
        .containsExactly("ratelimit:suggestion:" + userId);
  }
}
