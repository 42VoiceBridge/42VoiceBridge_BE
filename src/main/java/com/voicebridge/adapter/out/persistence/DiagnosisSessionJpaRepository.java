package com.voicebridge.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisSessionJpaRepository
    extends JpaRepository<DiagnosisSessionJpaEntity, UUID> {}
