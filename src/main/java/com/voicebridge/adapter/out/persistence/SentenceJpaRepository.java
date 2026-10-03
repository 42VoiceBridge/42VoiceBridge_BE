package com.voicebridge.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceJpaRepository extends JpaRepository<SentenceJpaEntity, UUID> {

  /** 문장 마스터 데이터에서 무작위로 count개를 뽑는다. 시드 데이터가 없으면 빈 리스트가 반환된다. */
  @Query(value = "SELECT * FROM sentences ORDER BY RAND() LIMIT :count", nativeQuery = true)
  List<SentenceJpaEntity> findRandom(@Param("count") int count);
}
