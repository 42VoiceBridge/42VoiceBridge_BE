package com.voicebridge.adapter.out.ratelimit;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.CallRateLimitPort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 카운터로 구현한 사용자별 호출 상한(고정 창). 키에는 사용자 ID와 기능 이름만 넣고 입력 텍스트는 넣지 않는다.
 *
 * <p>증가와 만료 설정을 Lua 스크립트 하나로 묶는다. 따로 보내면 그 사이에 서버가 죽었을 때 만료 없는 키가 남아 그 사용자가 영원히 막힌다.
 *
 * <p>Redis 장애 시에는 막는 쪽으로 실패한다(유료 API를 상한 없이 열어 두지 않는다).
 */
@Component
@RequiredArgsConstructor
public class RedisCallRateLimitAdapter implements CallRateLimitPort {

  private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL =
      new DefaultRedisScript<>(
          "local count = redis.call('INCR', KEYS[1]) "
              + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
              + "return count",
          Long.class);

  private final StringRedisTemplate redisTemplate;

  @Override
  public boolean tryAcquire(UUID userId, String feature, int limit, Duration window) {
    if (userId == null || feature == null || limit <= 0 || window.toMillis() <= 0) {
      throw new IllegalArgumentException("호출 상한 인자가 올바르지 않습니다.");
    }
    String key = "ratelimit:" + feature + ":" + userId;
    Long count;
    try {
      count =
          redisTemplate.execute(
              INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "호출 상한을 확인하지 못했습니다.");
    }
    return count != null && count <= limit;
  }
}
