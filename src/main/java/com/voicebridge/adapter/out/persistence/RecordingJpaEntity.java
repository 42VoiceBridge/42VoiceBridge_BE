package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.diagnosis.RecordingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// session_id 인덱스는 취약 음소 분석이 세션의 녹음 전체를 훑는 조회 패턴 때문에 필요하다.
@Entity
@Table(
    name = "recordings",
    indexes = @Index(name = "idx_recordings_session_id", columnList = "session_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordingJpaEntity {

  @Id private UUID id;

  private UUID sessionId;

  private UUID sentenceId;

  private UUID userId;

  // 명시하지 않으면 JPA 기본 규칙이 숫자 뒤 대문자를 단어 경계로 보지 않아 s3path로 만들어진다.
  // 다른 컬럼(session_id, created_at)과 규칙을 맞추기 위해 직접 지정한다.
  @Column(name = "s3_path")
  private String s3Path;

  @Enumerated(EnumType.STRING)
  private RecordingStatus status;

  @Column(length = 1000)
  private String recognizedText;

  private Double confidence;

  private LocalDateTime createdAt;

  @Builder
  private RecordingJpaEntity(
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
}
