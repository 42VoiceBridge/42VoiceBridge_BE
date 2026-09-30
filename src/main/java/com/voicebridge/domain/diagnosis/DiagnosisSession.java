package com.voicebridge.domain.diagnosis;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

  /**
   * ANALYZED는 "이 세션의 녹음이 모두 끝나 자모 오류 통계의 집계 대상이 되었다"는 뜻이다. 통계는 세션 하나가 아니라 사용자의 ANALYZED 세션들을 누적해서
   * 낸다.
   */
  public void markAnalyzed() {
    if (status != DiagnosisSessionStatus.IN_PROGRESS) {
      throw new IllegalStateException("진행중인 세션만 분석 완료 처리할 수 있습니다.");
    }
    this.status = DiagnosisSessionStatus.ANALYZED;
  }

  /**
   * 문장마다 가장 최근 녹음이 인식을 마쳤으면(DONE) ANALYZED로 전이하고 true를 돌려준다. 이미 전이된 세션이면 아무것도 하지 않고 false.
   *
   * <p>"모든 녹음"이 아니라 "문장마다 가장 최근 녹음"으로 보는 이유: 인식에 실패(FAILED)한 녹음은 같은 문장을 다시 녹음하면 되고, 다시 녹음한 것이 가장
   * 최근이 된다. 실패한 녹음이 남아 있다는 이유로 세션이 영영 끝나지 못하면 안 된다.
   *
   * <p>"문장마다 DONE 하나라도"로 보지 않는 이유: 다시 녹음한 것이 아직 인식 중인데 예전 DONE으로 세션을 끝내면, 분석이 끝난 뒤에 그 녹음의 결과가 들어와
   * 이미 계산한 통계의 재료가 바뀐다. 세션 조회 API도 문장마다 가장 최근 녹음을 보여주므로, 화면에 모든 문장이 DONE으로 보일 때가 곧 ANALYZED가 될 때다.
   */
  public boolean markAnalyzedIfAllSentencesDone(Collection<Recording> recordings) {
    if (status != DiagnosisSessionStatus.IN_PROGRESS) {
      return false;
    }
    Set<UUID> doneSentenceIds =
        Recording.latestPerSentence(recordings).stream()
            .filter(recording -> recording.getSessionId().equals(id))
            .filter(Recording::isDone)
            .map(Recording::getSentenceId)
            .collect(Collectors.toSet());
    if (!doneSentenceIds.containsAll(sentenceIds)) {
      return false;
    }
    markAnalyzed();
    return true;
  }

  /** 녹음은 진행 중인 세션에만 추가할 수 있다. 집계 대상이 된(ANALYZED) 뒤에 녹음이 바뀌면 이미 반영된 통계와 어긋난다. */
  public void ensureRecordable() {
    if (status != DiagnosisSessionStatus.IN_PROGRESS) {
      throw new IllegalStateException("분석이 끝난 세션에는 녹음을 추가할 수 없습니다.");
    }
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
