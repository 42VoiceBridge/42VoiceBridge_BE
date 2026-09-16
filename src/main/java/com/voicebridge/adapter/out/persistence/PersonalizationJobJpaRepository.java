package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.personalization.PersonalizationJobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalizationJobJpaRepository
    extends JpaRepository<PersonalizationJobJpaEntity, UUID> {

  Optional<PersonalizationJobJpaEntity> findFirstByUserIdAndStatusOrderByCompletedAtDesc(
      UUID userId, PersonalizationJobStatus status);

  Optional<PersonalizationJobJpaEntity> findFirstByUserIdAndStatus(
      UUID userId, PersonalizationJobStatus status);
}
