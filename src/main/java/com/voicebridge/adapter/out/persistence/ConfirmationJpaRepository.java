package com.voicebridge.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmationJpaRepository extends JpaRepository<ConfirmationJpaEntity, UUID> {
  Optional<ConfirmationJpaEntity> findByRecognitionIdAndValidTrue(UUID recognitionId);
}
