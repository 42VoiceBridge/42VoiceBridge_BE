package com.voicebridge.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingJpaRepository extends JpaRepository<RecordingJpaEntity, UUID> {

  List<RecordingJpaEntity> findBySessionId(UUID sessionId);

  List<RecordingJpaEntity> findBySessionIdIn(Collection<UUID> sessionIds);
}
