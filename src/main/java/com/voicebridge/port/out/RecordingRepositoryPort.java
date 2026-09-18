package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.Recording;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingRepositoryPort {

  Recording save(Recording recording);

  Optional<Recording> findById(UUID id);

  List<Recording> findBySessionId(UUID sessionId);
}
