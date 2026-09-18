package com.voicebridge.adapter.out.auth;

import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.TokenHasherPort;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenStoreAdapter implements RefreshTokenStorePort {

  private static final String KEY_PREFIX = "refresh_token:";

  private final StringRedisTemplate redisTemplate;
  private final TokenHasherPort tokenHasherPort;

  @Value("${voicebridge.jwt.refresh-token-expire-seconds}")
  private long refreshTokenExpireSeconds;

  @Override
  public void save(UUID userId, String refreshToken) {
    String tokenHash = tokenHasherPort.hash(refreshToken);
    redisTemplate
        .opsForValue()
        .set(key(userId), tokenHash, Duration.ofSeconds(refreshTokenExpireSeconds));
  }

  @Override
  public boolean isValid(UUID userId, String refreshToken) {
    String storedHash = redisTemplate.opsForValue().get(key(userId));
    return storedHash != null && tokenHasherPort.matches(refreshToken, storedHash);
  }

  @Override
  public void revoke(UUID userId) {
    redisTemplate.delete(key(userId));
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }
}
