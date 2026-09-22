package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.out.RecordingRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordingPersistenceAdapter implements RecordingRepositoryPort {

  private final RecordingJpaRepository jpaRepository;

  @Override
  public Recording save(Recording recording) {
    try {
      RecordingJpaEntity saved = jpaRepository.save(toEntity(recording));
      return toDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "저장 중 예상치 못한 제약 위반이 발생했습니다.");
    }
  }

  @Override
  public Optional<Recording> findById(UUID id) {
    return jpaRepository.findById(id).map(RecordingPersistenceAdapter::toDomain);
  }

  @Override
  public List<Recording> findBySessionId(UUID sessionId) {
    return jpaRepository.findBySessionId(sessionId).stream()
        .map(RecordingPersistenceAdapter::toDomain)
        .toList();
  }

  private static RecordingJpaEntity toEntity(Recording recording) {
    return RecordingJpaEntity.builder()
        .id(recording.getId())
        .sessionId(recording.getSessionId())
        .sentenceId(recording.getSentenceId())
        .userId(recording.getUserId())
        .s3Path(recording.getS3Path())
        .status(recording.getStatus())
        .recognizedText(recording.getRecognizedText())
        .confidence(recording.getConfidence())
        .createdAt(recording.getCreatedAt())
        .build();
  }

  private static Recording toDomain(RecordingJpaEntity entity) {
    return Recording.reconstitute(
        entity.getId(),
        entity.getSessionId(),
        entity.getSentenceId(),
        entity.getUserId(),
        entity.getS3Path(),
        entity.getStatus(),
        entity.getRecognizedText(),
        entity.getConfidence(),
        entity.getCreatedAt());
  }
}
