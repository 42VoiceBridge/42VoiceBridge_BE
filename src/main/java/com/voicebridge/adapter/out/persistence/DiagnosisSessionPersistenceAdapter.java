package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 현재는 DiagnosisSessionJpaEntity에 unique/FK 제약이 없어 발생 시나리오가 없는 방어 코드 — 제약 추가 시를 대비한 것. */
@Component
@RequiredArgsConstructor
public class DiagnosisSessionPersistenceAdapter implements DiagnosisSessionRepositoryPort {

  private final DiagnosisSessionJpaRepository jpaRepository;

  @Override
  public DiagnosisSession save(DiagnosisSession session) {
    try {
      DiagnosisSessionJpaEntity saved = jpaRepository.save(toEntity(session));
      return toDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "저장 중 예상치 못한 제약 위반이 발생했습니다.");
    }
  }

  @Override
  public Optional<DiagnosisSession> findById(UUID id) {
    return jpaRepository.findById(id).map(DiagnosisSessionPersistenceAdapter::toDomain);
  }

  private static DiagnosisSessionJpaEntity toEntity(DiagnosisSession session) {
    return DiagnosisSessionJpaEntity.builder()
        .id(session.getId())
        .userId(session.getUserId())
        .status(session.getStatus())
        .sentenceIds(session.getSentenceIds())
        .createdAt(session.getCreatedAt())
        .build();
  }

  private static DiagnosisSession toDomain(DiagnosisSessionJpaEntity entity) {
    return DiagnosisSession.reconstitute(
        entity.getId(),
        entity.getUserId(),
        entity.getStatus(),
        entity.getSentenceIds(),
        entity.getCreatedAt());
  }
}
