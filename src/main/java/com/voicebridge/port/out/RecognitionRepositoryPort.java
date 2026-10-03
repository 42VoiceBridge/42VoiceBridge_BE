package com.voicebridge.port.out;

import com.voicebridge.domain.recognition.Recognition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 인식 결과를 저장·조회하는 계약. JPA의 Page나 Pageable은 어댑터 내부에서 사용한다. */
public interface RecognitionRepositoryPort {
  Recognition save(Recognition recognition);

  Optional<Recognition> findById(UUID id);

  RecognitionPage findByUserId(UUID userId, int page, int size);

  record RecognitionPage(List<Recognition> content, long totalElements, int totalPages) {}
}
