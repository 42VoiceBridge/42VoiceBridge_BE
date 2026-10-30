package com.voicebridge.adapter.out.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.conversation.ConversationTurn;
import com.voicebridge.port.out.ConversationTurnStorePort;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 대화 턴을 Redis에 JSON으로 TTL과 함께 저장한다. 키에는 턴 ID만 넣는다. 들은 말·후보는 값에만 있고, TTL이 지나면 Redis가 지운다.
 *
 * <p>Redis 장애와 깨진 값은 CustomException으로 번역한다.
 */
@Component
@RequiredArgsConstructor
public class RedisConversationTurnStoreAdapter implements ConversationTurnStorePort {

  static final String KEY_PREFIX = "conversation:turn:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public void save(ConversationTurn turn, Duration ttl) {
    String json;
    try {
      json = objectMapper.writeValueAsString(StoredTurn.from(turn));
    } catch (JsonProcessingException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "대화 턴을 저장하지 못했습니다.");
    }
    try {
      redisTemplate.opsForValue().set(KEY_PREFIX + turn.getId(), json, ttl);
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "대화 턴을 저장하지 못했습니다.");
    }
  }

  @Override
  public Optional<ConversationTurn> findById(UUID turnId) {
    String json;
    try {
      json = redisTemplate.opsForValue().get(KEY_PREFIX + turnId);
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "대화 턴을 읽지 못했습니다.");
    }
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, StoredTurn.class).toDomain());
    } catch (JsonProcessingException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "대화 턴을 읽지 못했습니다.");
    }
  }

  // 도메인 객체를 직접 직렬화하지 않는다. 저장 형식이 도메인 필드 이름에 묶이지 않게 따로 둔다.
  record StoredTurn(
      UUID id,
      UUID ownerId,
      String heardText,
      List<StoredCandidate> candidates,
      LocalDateTime createdAt) {

    static StoredTurn from(ConversationTurn turn) {
      return new StoredTurn(
          turn.getId(),
          turn.getOwnerId(),
          turn.getHeardText(),
          turn.getCandidates().stream().map(c -> new StoredCandidate(c.id(), c.text())).toList(),
          turn.getCreatedAt());
    }

    ConversationTurn toDomain() {
      return ConversationTurn.reconstitute(
          id,
          ownerId,
          heardText,
          candidates.stream().map(c -> new ConversationTurn.Candidate(c.id(), c.text())).toList(),
          createdAt);
    }
  }

  record StoredCandidate(UUID id, String text) {}
}
