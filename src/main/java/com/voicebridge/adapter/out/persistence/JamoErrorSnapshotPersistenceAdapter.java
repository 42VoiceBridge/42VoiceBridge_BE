package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.JamoErrorSnapshot;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import com.voicebridge.port.out.JamoErrorSnapshotRepositoryPort;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JamoErrorSnapshotPersistenceAdapter implements JamoErrorSnapshotRepositoryPort {

  private final JamoErrorSnapshotJpaRepository jpaRepository;

  @Override
  public Optional<JamoErrorSnapshot> findByUserId(UUID userId) {
    return jpaRepository.findById(userId).map(JamoErrorSnapshotPersistenceAdapter::toDomain);
  }

  // 같은 사용자의 첫 스냅샷을 두 요청이 동시에 저장하면 기본키 충돌이 날 수 있다.
  @Override
  public JamoErrorSnapshot save(JamoErrorSnapshot snapshot) {
    try {
      return toDomain(jpaRepository.save(toEntity(snapshot)));
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "자모 오류 통계를 저장하지 못했습니다.");
    }
  }

  private static JamoErrorSnapshotJpaEntity toEntity(JamoErrorSnapshot snapshot) {
    return JamoErrorSnapshotJpaEntity.builder()
        .userId(snapshot.getUserId())
        .metricVersion(snapshot.getMetricVersion())
        .minSupport(snapshot.getMinSupport())
        .sessionsUsed(snapshot.getSessionsUsed())
        .pairsUsed(snapshot.getPairsUsed())
        // JPA가 컬렉션을 직접 고치므로 도메인의 불변 리스트를 그대로 넘기지 않는다
        .tokens(
            new ArrayList<>(
                snapshot.getTokens().stream()
                    .map(
                        t ->
                            new JamoErrorTokenEmbeddable(
                                t.token(),
                                t.position(),
                                t.errors(),
                                t.sampleCount(),
                                t.errorRate(),
                                t.status()))
                    .toList()))
        .computedAt(snapshot.getComputedAt())
        .build();
  }

  private static JamoErrorSnapshot toDomain(JamoErrorSnapshotJpaEntity entity) {
    return JamoErrorSnapshot.reconstitute(
        entity.getUserId(),
        entity.getMetricVersion(),
        entity.getMinSupport(),
        entity.getSessionsUsed(),
        entity.getPairsUsed(),
        entity.getTokens().stream()
            .map(
                t ->
                    new JamoErrorStat(
                        t.getToken(),
                        t.getPosition(),
                        t.getErrors(),
                        t.getSampleCount(),
                        t.getErrorRate(),
                        t.getStatus()))
            .toList(),
        entity.getComputedAt());
  }
}
