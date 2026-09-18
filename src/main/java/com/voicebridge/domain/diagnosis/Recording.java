package com.voicebridge.domain.diagnosis;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 진단 세션 내 녹음 하나. 상태 전이(UPLOADED → PROCESSING → DONE)를 이 클래스가 직접 소유한다(Rich Domain Model) — Service는
 * 이 메서드들을 호출만 한다. 소유자(userId)는 들고 있지 않는다 — 소속된 DiagnosisSession이 이미 갖고 있으므로 중복 저장하지 않는다(SSOT).
 */
public class Recording {

  private final UUID id;
  private final UUID sessionId;
  private final UUID sentenceId;
  private final String storageKey;
  private RecordingStatus status;
  private String recognizedText;
  private Double confidence;
  private final LocalDateTime createdAt;

  private Recording(
      UUID id,
      UUID sessionId,
      UUID sentenceId,
      String storageKey,
      RecordingStatus status,
      String recognizedText,
      Double confidence,
      LocalDateTime createdAt) {
    this.id = id;
    this.sessionId = sessionId;
    this.sentenceId = sentenceId;
    this.storageKey = storageKey;
    this.status = status;
    this.recognizedText = recognizedText;
    this.confidence = confidence;
    this.createdAt = createdAt;
  }

  /** 오디오 파일이 스토리지(S3)에 업로드된 직후 호출한다. storageKey는 스토리지 어댑터가 발급한 객체 키다. */
  public static Recording upload(UUID sessionId, UUID sentenceId, String storageKey) {
    if (sessionId == null || sentenceId == null) {
      throw new IllegalArgumentException("녹음은 세션과 문장에 반드시 속해야 합니다.");
    }
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("업로드된 오디오의 저장 위치가 필요합니다.");
    }
    return new Recording(
        UUID.randomUUID(),
        sessionId,
        sentenceId,
        storageKey,
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
      String storageKey,
      RecordingStatus status,
      String recognizedText,
      Double confidence,
      LocalDateTime createdAt) {
    return new Recording(
        id, sessionId, sentenceId, storageKey, status, recognizedText, confidence, createdAt);
  }

  /** AI 추론 요청을 보내기 직전에 호출한다. */
  public void markProcessing() {
    if (status != RecordingStatus.UPLOADED) {
      throw new IllegalStateException("업로드 직후 상태에서만 인식을 시작할 수 있습니다.");
    }
    this.status = RecordingStatus.PROCESSING;
  }

  /** AI 추론 결과를 받았을 때 호출한다. */
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

  /** 세션의 모든 녹음이 끝났는지(AnalyzeWeakPhonemesUseCase 전제 조건) 확인할 때 사용한다. */
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

  public String getStorageKey() {
    return storageKey;
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
