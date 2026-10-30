package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.phrase.PhraseCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhraseJpaRepository extends JpaRepository<PhraseJpaEntity, UUID> {

  @Query(
      "SELECT p FROM PhraseJpaEntity p"
          + " WHERE (p.ownerId IS NULL OR p.ownerId = :userId)"
          + " AND (:category IS NULL OR p.category = :category)"
          + " ORDER BY p.createdAt, p.id")
  List<PhraseJpaEntity> findAllVisibleTo(
      @Param("userId") UUID userId, @Param("category") PhraseCategory category);

  boolean existsByOwnerIdAndCategoryAndTextSha256(
      UUID ownerId, PhraseCategory category, String textSha256);

  long countByOwnerId(UUID ownerId);

  long countByOwnerIdIsNull();
}
