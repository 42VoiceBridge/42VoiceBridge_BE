package com.voicebridge.domain.diagnosis;

import java.time.LocalDateTime;
import java.util.UUID;

public class Recording {

  private final UUID id;
  private final UUID sessionId;
  private final UUID sentenceId;

  // DiagnosisSession에도 있는 값이지만, 결과 조회 시 세션까지 조회하지 않고 소유권을 검증하려고
  // 의도적으로 중복 보관한다(비정규화). 대신 create() 호출 전에 세션 소유권을 반드시 먼저 검증해야 한다.
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

  public void markProcessing() {
    if (status != RecordingStatus.UPLOADED) {
      throw new IllegalStateException("업로드 직후 상태에서만 인식을 시작할 수 있습니다.");
    }
    this.status = RecordingStatus.PROCESSING;
  }

  public void markProcessed(String recognizedText, Double confidence) {
    if (status != RecordingStatus.PROCESSING) {
      throw new IllegalStateException("인식이 진행중인 녹음만 결과를 반영할 수 있습니다.");
    }
    // 빈 문자열은 무음·비언어 오디오의 정상 응답이므로 막지 않는다
    if (recognizedText == null) {
      throw new IllegalArgumentException("인식 결과 텍스트는 null일 수 없습니다.");
    }
    // confidence는 AI 계약상 null일 수 있다(v1은 항상 null) — null이 아닐 때만 범위를 검증한다.
    if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
      throw new IllegalArgumentException("인식 신뢰도는 0과 1 사이여야 합니다.");
    }
    this.recognizedText = recognizedText;
    this.confidence = confidence;
    this.status = RecordingStatus.DONE;
  }

  // 비동기 인식이라 실패를 HTTP 응답으로 알릴 수 없다. 상태로 남겨야 결과 조회 API가 알려줄 수 있다.
  public void markFailed() {
    if (status != RecordingStatus.PROCESSING) {
      throw new IllegalStateException("인식이 진행중인 녹음만 실패 처리할 수 있습니다.");
    }
    this.status = RecordingStatus.FAILED;
  }

  public boolean isOwnedBy(UUID userId) {
    return this.userId.equals(userId);
  }

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
