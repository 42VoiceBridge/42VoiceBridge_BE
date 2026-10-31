package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.StorageDeletionQueuePort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StorageDeletionQueuePersistenceAdapter implements StorageDeletionQueuePort {

  private final StorageDeletionJpaRepository repository;

  @Override
  @Transactional
  public void enqueue(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("저장소 key가 필요합니다.");
    }
    try {
      if (!repository.existsById(storageKey)) {
        repository.save(new StorageDeletionJpaEntity(storageKey, LocalDateTime.now()));
      }
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 삭제 재시도를 기록하지 못했습니다.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<QueuedDeletion> findOldest(int limit) {
    try {
      return repository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, limit)).stream()
          .map(e -> new QueuedDeletion(e.getStorageKey(), e.getAttempts()))
          .toList();
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 삭제 재시도 목록을 읽지 못했습니다.");
    }
  }

  @Override
  @Transactional
  public void remove(String storageKey) {
    try {
      repository.deleteById(storageKey);
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 삭제 재시도 기록을 지우지 못했습니다.");
    }
  }

  @Override
  @Transactional
  public int recordFailure(String storageKey) {
    try {
      StorageDeletionJpaEntity entity =
          repository
              .findById(storageKey)
              .orElseGet(() -> new StorageDeletionJpaEntity(storageKey, LocalDateTime.now()));
      entity.recordFailure(LocalDateTime.now());
      return repository.save(entity).getAttempts();
    } catch (DataAccessException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 삭제 재시도를 기록하지 못했습니다.");
    }
  }
}
