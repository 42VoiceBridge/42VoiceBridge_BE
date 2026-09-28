package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosisSessionRepositoryPort {

  DiagnosisSession save(DiagnosisSession session);

  Optional<DiagnosisSession> findById(UUID id);

  List<DiagnosisSession> findByUserIdAndStatusIn(
      UUID userId, Collection<DiagnosisSessionStatus> statuses);
}
