package com.voicebridge.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JamoErrorSnapshotJpaRepository
    extends JpaRepository<JamoErrorSnapshotJpaEntity, UUID> {}
