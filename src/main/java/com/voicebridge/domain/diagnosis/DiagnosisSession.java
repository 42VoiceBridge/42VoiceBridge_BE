package com.voicebridge.domain.diagnosis;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 진단 세션 도메인 엔티티. 상태 전이 규칙(IN_PROGRESS → ANALYZED → COMPLETED)을 이 클래스가 직접 소유한다(Rich Domain Model) —
 * Service는 이 메서드들을 호출만 한다.
 */
public class DiagnosisSession {

  private final UUID id;
  private final UUID userId;
  private DiagnosisSessionStatus status;
  private final List<UUID> sentenceIds;
  private final LocalDateTime createdAt;

  private DiagnosisSession(
      UUID id,
      UUID userId,
      DiagnosisSessionStatus status,
      List<UUID> sentenceIds,
      LocalDateTime createdAt) {
    this.id = id;
    this.userId = userId;
    this.status = status;
    this.sentenceIds = sentenceIds;
    this.createdAt = createdAt;
  }

  /** 낭독할 문장 목록이 정해진 상태로만 세션을 시작할 수 있다. */
  public static DiagnosisSession start(UUID userId, List<UUID> sentenceIds) {
    if (sentenceIds == null || sentenceIds.isEmpty()) {
      throw new IllegalArgumentException("진단 세션은 최소 1개 이상의 문장이 필요합니다.");
    }
    return new DiagnosisSession(
        UUID.randomUUID(),
        userId,
        DiagnosisSessionStatus.IN_PROGRESS,
        sentenceIds,
        LocalDateTime.now());
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
  public static DiagnosisSession reconstitute(
      UUID id,
      UUID userId,
      DiagnosisSessionStatus status,
      List<UUID> sentenceIds,
      LocalDateTime createdAt) {
    return new DiagnosisSession(id, userId, status, sentenceIds, createdAt);
  }

  /** 세션의 모든 녹음이 인식 완료됐을 때 호출한다 (AnalyzeWeakPhonemesUseCase의 전제 조건). */
  public void markAnalyzed() {
    if (status != DiagnosisSessionStatus.IN_PROGRESS) {
      throw new IllegalStateException("진행중인 세션만 분석 완료 처리할 수 있습니다.");
    }
    this.status = DiagnosisSessionStatus.ANALYZED;
  }

  public void complete() {
    if (status != DiagnosisSessionStatus.ANALYZED) {
      throw new IllegalStateException("분석이 끝난 세션만 완료 처리할 수 있습니다.");
    }
    this.status = DiagnosisSessionStatus.COMPLETED;
  }

  public boolean isOwnedBy(UUID userId) {
    return this.userId.equals(userId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public DiagnosisSessionStatus getStatus() {
    return status;
  }

  public List<UUID> getSentenceIds() {
    return sentenceIds;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
