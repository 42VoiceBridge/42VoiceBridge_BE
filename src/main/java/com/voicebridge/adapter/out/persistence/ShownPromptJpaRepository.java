package com.voicebridge.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShownPromptJpaRepository extends JpaRepository<ShownPromptJpaEntity, UUID> {

  // 원문까지 읽을 필요가 없어 문장 ID 열만 가져온다
  @Query("select s.promptId from ShownPromptJpaEntity s where s.userId = :userId")
  List<String> findPromptIdsByUserId(UUID userId);
}
