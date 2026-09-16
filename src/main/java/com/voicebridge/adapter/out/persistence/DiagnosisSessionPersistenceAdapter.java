package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiagnosisSessionPersistenceAdapter implements DiagnosisSessionRepositoryPort {

  private final DiagnosisSessionJpaRepository jpaRepository;

  @Override
  public DiagnosisSession save(DiagnosisSession session) {
    DiagnosisSessionJpaEntity saved = jpaRepository.save(toEntity(session));
    return toDomain(saved);
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
