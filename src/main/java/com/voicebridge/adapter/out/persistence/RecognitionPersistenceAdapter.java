package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.Recognition;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/*
 * Recognition 객체 받아서 DB에 저장하고 다시 객체 돌려주는 어댑터 클래스
 * Port의 계약 구현체 Recognition 객체를 받아서 JPA엔티티에 맞춰서 DB 저장하고
 * 저장 메서드가 반환한 엔티티를 다시 도메인 객체로 변환해서 리턴
 * */

// 스프링에 빈으로 어댑터를 등록 및 생성자 코드 생성
@Component
@RequiredArgsConstructor
public class RecognitionPersistenceAdapter implements RecognitionRepositoryPort {
  private final RecognitionJpaRepository jpaRepository;

  // Spring Data JPA가 제공하는 저장소 구현 객체를 생성자로 주입받는다.
  /*
   * Port의 Save 메서드 구현, JpaRepository 상속받은 RecognitionJpaRepository 객체를 생성자로 주입받음 DI?
   * @Transactional로 DB저장을 트랜잭션을 관리하게 함
   * */
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
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "인식 결과 저장에 실패했습니다.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Recognition> findById(UUID id) {
    try {
      return jpaRepository.findById(id).map(RecognitionPersistenceAdapter::toDomain);
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "인식 결과 조회에 실패했습니다.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public RecognitionPage findByUserId(UUID userId, int page, int size) {
    try {
      var pageable =
          PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
      // 사용자 조건과 페이지 범위를 DB 조회에 적용한다.
      var result = jpaRepository.findByUserId(userId, pageable);
      return new RecognitionPage(
          result.getContent().stream().map(RecognitionPersistenceAdapter::toDomain).toList(),
          result.getTotalElements(),
          result.getTotalPages());
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "인식 이력 조회에 실패했습니다.");
    }
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
