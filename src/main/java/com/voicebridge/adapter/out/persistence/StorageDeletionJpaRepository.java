package com.voicebridge.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageDeletionJpaRepository
    extends JpaRepository<StorageDeletionJpaEntity, String> {

  List<StorageDeletionJpaEntity> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
