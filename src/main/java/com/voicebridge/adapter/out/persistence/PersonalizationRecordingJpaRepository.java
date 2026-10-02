package com.voicebridge.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PersonalizationRecordingJpaRepository
    extends JpaRepository<PersonalizationRecordingJpaEntity, UUID> {
  @Modifying
  @Query(
      "update PersonalizationRecordingJpaEntity r set r.status = 'UPLOADED' where r.id = :id and r.status = 'PREPARING'")
  int markUploadedIfPreparing(UUID id);

  @Modifying
  @Query(
      "update PersonalizationRecordingJpaEntity r set r.status = 'CLEANUP_PENDING' where r.id = :id and r.createdAt < :cutoff and r.status in ('PREPARING', 'CLEANUP_PENDING', 'DELETION_PENDING')")
  int claimPendingForCleanup(UUID id, LocalDateTime cutoff);

  List<PersonalizationRecordingJpaEntity> findByStatusAndCreatedAtBefore(
      String status, LocalDateTime cutoff);

  List<PersonalizationRecordingJpaEntity> findByStatusInAndCreatedAtBefore(
      List<String> statuses, LocalDateTime cutoff);
}
