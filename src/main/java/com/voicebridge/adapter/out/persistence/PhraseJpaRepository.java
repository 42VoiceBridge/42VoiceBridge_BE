package com.voicebridge.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhraseJpaRepository extends JpaRepository<PhraseJpaEntity, UUID> {

  @Query(
      "SELECT p FROM PhraseJpaEntity p WHERE p.ownerId IS NULL OR p.ownerId = :userId"
          + " ORDER BY p.createdAt, p.id")
  List<PhraseJpaEntity> findAllVisibleTo(@Param("userId") UUID userId);

  @Query(
      "SELECT COUNT(p) > 0 FROM PhraseJpaEntity p"
          + " WHERE (p.ownerId IS NULL OR p.ownerId = :userId) AND p.text = :text")
  boolean existsVisibleText(@Param("userId") UUID userId, @Param("text") String text);

  long countByOwnerId(UUID ownerId);

  long countByOwnerIdIsNull();
}
