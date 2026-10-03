package com.voicebridge.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecognitionJpaRepository extends JpaRepository<RecognitionJpaEntity, UUID> {
  Page<RecognitionJpaEntity> findByUserId(UUID userId, Pageable pageable);
}
