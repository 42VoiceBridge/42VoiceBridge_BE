package com.voicebridge.adapter.out.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

@Testcontainers
@SpringBootTest
class RefreshTokenStoreAdapterTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired private RefreshTokenStoreAdapter refreshTokenStoreAdapter;

  @Autowired private StringRedisTemplate redisTemplate;

  @org.springframework.beans.factory.annotation.Value(
      "${voicebridge.jwt.refresh-token-expire-seconds}")
  private long refreshTokenExpireSeconds;

  @Test
  void 저장한_토큰으로_isValid를_호출하면_true를_반환한다() {
    UUID userId = UUID.randomUUID();
    String refreshToken = "raw-refresh-token";

    refreshTokenStoreAdapter.save(userId, refreshToken);

    assertThat(refreshTokenStoreAdapter.isValid(userId, refreshToken)).isTrue();
  }

  @Test
  void 다른_토큰으로_isValid를_호출하면_false를_반환한다() {
    UUID userId = UUID.randomUUID();
    refreshTokenStoreAdapter.save(userId, "raw-refresh-token");

    assertThat(refreshTokenStoreAdapter.isValid(userId, "다른-토큰")).isFalse();
  }

  @Test
  void 저장된적_없는_사용자는_isValid가_false를_반환한다() {
    assertThat(refreshTokenStoreAdapter.isValid(UUID.randomUUID(), "아무-토큰")).isFalse();
  }

  @Test
  void revoke하면_이후_isValid가_false를_반환한다() {
    UUID userId = UUID.randomUUID();
    String refreshToken = "raw-refresh-token";
    refreshTokenStoreAdapter.save(userId, refreshToken);

    refreshTokenStoreAdapter.revoke(userId);

    assertThat(refreshTokenStoreAdapter.isValid(userId, refreshToken)).isFalse();
  }

  @Test
  void 저장시_설정된_만료시간으로_TTL이_설정된다() {
    UUID userId = UUID.randomUUID();
    refreshTokenStoreAdapter.save(userId, "raw-refresh-token");

    Long ttl = redisTemplate.getExpire("refresh_token:" + userId, TimeUnit.SECONDS);

    assertThat(ttl).isNotNull();
    assertThat(ttl).isPositive().isLessThanOrEqualTo(refreshTokenExpireSeconds);
    assertThat(ttl).isGreaterThan(refreshTokenExpireSeconds - 10);
  }
}
