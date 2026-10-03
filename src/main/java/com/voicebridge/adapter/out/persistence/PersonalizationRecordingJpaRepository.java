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
      "update PersonalizationRecordingJpaEntity r set r.status = :next where r.id = :id and r.status in :allowedSources")
  int transitionIfAllowed(UUID id, String next, List<String> allowedSources);

  @Modifying
  @Query(
      "update PersonalizationRecordingJpaEntity r set r.status = :next where r.id = :id and r.createdAt < :cutoff and r.status in :allowedSources")
  int transitionIfAllowedBefore(
      UUID id, LocalDateTime cutoff, String next, List<String> allowedSources);

  List<PersonalizationRecordingJpaEntity> findByStatusAndCreatedAtBefore(
      String status, LocalDateTime cutoff);

  List<PersonalizationRecordingJpaEntity>
      findByUserIdAndStatusAndUseForTrainingTrueAndCreatedAtAfter(
          UUID userId, String status, LocalDateTime cutoff);

  List<PersonalizationRecordingJpaEntity> findByStatusInAndCreatedAtBefore(
      List<String> statuses, LocalDateTime cutoff);
}
