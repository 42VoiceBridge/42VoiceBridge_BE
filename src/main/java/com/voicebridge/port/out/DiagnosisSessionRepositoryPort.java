package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosisSessionRepositoryPort {

  DiagnosisSession save(DiagnosisSession session);

  Optional<DiagnosisSession> findById(UUID id);
}
