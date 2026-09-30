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

  /**
   * 같은 사용자의 첫 스냅샷을 두 요청이 동시에 저장하면, 둘 다 "행 없음"을 보고 INSERT해서 늦은 쪽이 기본키 충돌로 실패한다. 그 시점엔 먼저 온 요청이 행을
   * 만들어 둔 뒤라, 한 번 더 저장하면 UPDATE가 되어 포트 계약(있으면 통째로 바꾼다)대로 끝난다. 그 사이 세션이 끝나 두 계산이 달라져도, 다음 조회의 낡음 검사가
   * 바로잡으므로 어느 쪽 값이 남아도 된다.
   *
   * <p>다시 저장할 수 있는 건 호출하는 서비스에 트랜잭션이 없어서 save()가 자기 트랜잭션에서 커밋까지 끝내기 때문이다. 바깥 트랜잭션 안에서 부르면 첫 실패가 그
   * 트랜잭션을 롤백 전용으로 만들어 재시도도 실패한다.
   */
  @Override
  public JamoErrorSnapshot save(JamoErrorSnapshot snapshot) {
    try {
      return toDomain(jpaRepository.save(toEntity(snapshot)));
    } catch (DataIntegrityViolationException createdByAnotherRequest) {
      return saveOverExisting(snapshot);
    }
  }

  private JamoErrorSnapshot saveOverExisting(JamoErrorSnapshot snapshot) {
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
