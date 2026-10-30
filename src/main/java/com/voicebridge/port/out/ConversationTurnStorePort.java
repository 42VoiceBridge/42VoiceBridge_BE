package com.voicebridge.port.out;

import com.voicebridge.domain.conversation.ConversationTurn;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** 대화 턴 임시 저장소(TTL). 만료된 턴은 없는 것과 같다. 저장소 장애는 CustomException으로 번역해 던진다. */
public interface ConversationTurnStorePort {

  void save(ConversationTurn turn, Duration ttl);

  Optional<ConversationTurn> findById(UUID turnId);
}
