package com.voicebridge.domain.diagnosis;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 진단 세션 내 녹음 하나. 상태 전이(UPLOADED → PROCESSING → DONE)를 이 클래스가 직접 소유한다(Rich Domain Model) — Service는
 * 이 메서드들을 호출만 한다.
 */
public class Recording {

  private final UUID id;
  private final UUID sessionId;
  private final UUID sentenceId;
  private final UUID userId;
  private final String s3Path;
  private RecordingStatus status;
  private String recognizedText;
  private Double confidence;
  private final LocalDateTime createdAt;

  private Recording(
      UUID id,
      UUID sessionId,
      UUID sentenceId,
      UUID userId,
      String s3Path,
      RecordingStatus status,
      String recognizedText,
      Double confidence,
      LocalDateTime createdAt) {
    this.id = id;
    this.sessionId = sessionId;
    this.sentenceId = sentenceId;
    this.userId = userId;
    this.s3Path = s3Path;
    this.status = status;
    this.recognizedText = recognizedText;
    this.confidence = confidence;
    this.createdAt = createdAt;
  }

  /** 오디오 파일이 S3에 업로드된 직후 호출한다. s3Path는 StoragePort가 반환한 저장 경로다. */
  public static Recording create(UUID sessionId, UUID sentenceId, UUID userId, String s3Path) {
    if (sessionId == null || sentenceId == null || userId == null) {
      throw new IllegalArgumentException("녹음은 세션, 문장, 사용자에 반드시 속해야 합니다.");
    }
    if (s3Path == null || s3Path.isBlank()) {
      throw new IllegalArgumentException("업로드된 오디오의 저장 경로가 필요합니다.");
    }
    return new Recording(
        UUID.randomUUID(),
        sessionId,
        sentenceId,
        userId,
        s3Path,
        RecordingStatus.UPLOADED,
        null,
        null,
        LocalDateTime.now());
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
  public static Recording reconstitute(
      UUID id,
      UUID sessionId,
      UUID sentenceId,
      UUID userId,
      String s3Path,
      RecordingStatus status,
      String recognizedText,
      Double confidence,
      LocalDateTime createdAt) {
    return new Recording(
        id, sessionId, sentenceId, userId, s3Path, status, recognizedText, confidence, createdAt);
  }

  /** AI 인식을 비동기로 트리거하기 직전에 호출한다. */
  public void markProcessing() {
    if (status != RecordingStatus.UPLOADED) {
      throw new IllegalStateException("업로드 직후 상태에서만 인식을 시작할 수 있습니다.");
    }
    this.status = RecordingStatus.PROCESSING;
  }

  /** AI 인식 결과를 받았을 때 호출한다. */
  public void markProcessed(String recognizedText, double confidence) {
    if (status != RecordingStatus.PROCESSING) {
      throw new IllegalStateException("인식이 진행중인 녹음만 결과를 반영할 수 있습니다.");
    }
    if (recognizedText == null || recognizedText.isBlank()) {
      throw new IllegalArgumentException("인식 결과 텍스트가 비어 있을 수 없습니다.");
    }
    this.recognizedText = recognizedText;
    this.confidence = confidence;
    this.status = RecordingStatus.DONE;
  }

  public boolean isOwnedBy(UUID userId) {
    return this.userId.equals(userId);
  }

  /** 세션의 모든 녹음이 끝났는지(취약 음소 분석의 전제 조건) 확인할 때 사용한다. */
  public boolean isDone() {
    return status == RecordingStatus.DONE;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public UUID getSentenceId() {
    return sentenceId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getS3Path() {
    return s3Path;
  }

  public RecordingStatus getStatus() {
    return status;
  }

  public String getRecognizedText() {
    return recognizedText;
  }

  public Double getConfidence() {
    return confidence;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
