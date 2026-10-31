package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "storage_deletion_queue",
    indexes = @Index(name = "idx_storage_deletion_queue_created_at", columnList = "createdAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageDeletionJpaEntity {

  @Id
  @Column(nullable = false)
  private String storageKey;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime lastAttemptAt;

  StorageDeletionJpaEntity(String storageKey, LocalDateTime createdAt) {
    this.storageKey = storageKey;
    this.attempts = 0;
    this.createdAt = createdAt;
  }

  void recordFailure(LocalDateTime at) {
    this.attempts++;
    this.lastAttemptAt = at;
  }
}
