package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.Recognition;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecognitionPersistenceAdapter implements RecognitionRepositoryPort {
  private final RecognitionJpaRepository jpaRepository;

  @Override
  @Transactional
  public Recognition save(Recognition recognition) {
    try {
      // flush까지 수행해 제약 위반이 어댑터 안에서 예외로 번역되게 한다.
      var saved =
          jpaRepository.saveAndFlush(
              RecognitionJpaEntity.builder()
                  .id(recognition.getId())
                  .userId(recognition.getUserId())
                  .recognizedText(recognition.getRecognizedText())
                  .modelUsed(recognition.getModelUsed())
                  .confidence(recognition.getConfidence())
                  .createdAt(recognition.getCreatedAt())
                  .build());
      return toDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "인식 결과 저장에 실패했습니다.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Recognition> findById(UUID id) {
    return jpaRepository.findById(id).map(RecognitionPersistenceAdapter::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public RecognitionPage findByUserId(UUID userId, int page, int size) {
    var pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    var result = jpaRepository.findByUserId(userId, pageable);
    return new RecognitionPage(
        result.getContent().stream().map(RecognitionPersistenceAdapter::toDomain).toList(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  private static Recognition toDomain(RecognitionJpaEntity entity) {
    return Recognition.reconstitute(
        entity.getId(),
        entity.getUserId(),
        entity.getRecognizedText(),
        entity.getModelUsed(),
        entity.getConfidence(),
        entity.getCreatedAt());
  }
}
