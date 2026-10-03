package com.voicebridge.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalizationAdapterJpaRepository
    extends JpaRepository<PersonalizationAdapterJpaEntity, UUID> {
  Optional<PersonalizationAdapterJpaEntity> findByActiveUserId(UUID userId);
}
