package com.voicebridge.port.out;

import com.voicebridge.domain.recognition.TtsRequest;
import java.util.Optional;
import java.util.UUID;

/** TTS 요청을 저장·조회하는 계약. idempotencyKey 조회는 중복 요청 처리를 위해 필요하다. */
public interface TtsRequestRepositoryPort {
  TtsRequest save(TtsRequest ttsRequest);

  Optional<TtsRequest> findById(UUID id);

  Optional<TtsRequest> findByIdempotencyKey(UUID idempotencyKey);
}
