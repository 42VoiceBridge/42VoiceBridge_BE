package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.Recording;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingRepositoryPort {

  Recording save(Recording recording);

  Optional<Recording> findById(UUID id);

  List<Recording> findBySessionId(UUID sessionId);

  /** 여러 세션의 녹음을 한 번에 가져온다. 세션마다 따로 조회하면 세션 수만큼 쿼리가 나간다. */
  List<Recording> findBySessionIdIn(Collection<UUID> sessionIds);
}
