package com.voicebridge.port.out;

import com.voicebridge.domain.recognition.Confirmation;
import java.util.Optional;
import java.util.UUID;

/** 인식 결과 확인(Confirmation) 내역을 저장·조회하는 계약. */
public interface ConfirmationRepositoryPort {
  Confirmation save(Confirmation confirmation);

  Optional<Confirmation> findById(UUID id);

  Optional<Confirmation> findValidByRecognitionId(UUID recognitionId);
}
