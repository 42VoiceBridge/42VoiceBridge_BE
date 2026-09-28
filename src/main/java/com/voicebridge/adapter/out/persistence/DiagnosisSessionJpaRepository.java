package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisSessionJpaRepository
    extends JpaRepository<DiagnosisSessionJpaEntity, UUID> {

  List<DiagnosisSessionJpaEntity> findByUserIdAndStatusIn(
      UUID userId, Collection<DiagnosisSessionStatus> statuses);
}
