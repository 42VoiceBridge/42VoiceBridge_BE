package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosisSessionJpaRepository
    extends JpaRepository<DiagnosisSessionJpaEntity, UUID> {

  List<DiagnosisSessionJpaEntity> findByUserIdAndStatusIn(
      UUID userId, Collection<DiagnosisSessionStatus> statuses);

  // SELECT ... FOR UPDATE. 세션 행 하나만 잠근다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from DiagnosisSessionJpaEntity s where s.id = :id")
  Optional<DiagnosisSessionJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
