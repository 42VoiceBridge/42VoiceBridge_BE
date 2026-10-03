package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자당 최신 스냅샷 하나만 두므로 user_id가 곧 기본키다. 조회는 항상 사용자 ID로 하나를 찾는 것뿐이라 별도 인덱스가 필요 없다.
@Entity
@Table(name = "jamo_error_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JamoErrorSnapshotJpaEntity {

  @Id private UUID userId;

  private String metricVersion;

  private int minSupport;

  private int sessionsUsed;

  private int pairsUsed;

  // 스냅샷은 항상 자모 행과 함께 쓰인다. open-in-view가 꺼져 있어 LAZY면 도메인으로 바꾸는 시점에 이미 연결이 닫혀 읽지 못한다.
  // 사용자당 한 건만 읽으므로 EAGER여도 N+1 문제가 생기지 않는다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "jamo_error_snapshot_tokens", joinColumns = @JoinColumn(name = "user_id"))
  @OrderColumn(name = "token_order")
  private List<JamoErrorTokenEmbeddable> tokens;

  private LocalDateTime computedAt;

  @Builder
  private JamoErrorSnapshotJpaEntity(
      UUID userId,
      String metricVersion,
      int minSupport,
      int sessionsUsed,
      int pairsUsed,
      List<JamoErrorTokenEmbeddable> tokens,
      LocalDateTime computedAt) {
    this.userId = userId;
    this.metricVersion = metricVersion;
    this.minSupport = minSupport;
    this.sessionsUsed = sessionsUsed;
    this.pairsUsed = pairsUsed;
    this.tokens = tokens;
    this.computedAt = computedAt;
  }
}
