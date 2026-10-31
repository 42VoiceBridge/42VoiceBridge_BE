package com.voicebridge.application;

import com.voicebridge.domain.storage.StorageDeletionPolicy;
import com.voicebridge.port.in.RetryStorageDeletionUseCase;
import com.voicebridge.port.out.StorageDeletionQueuePort;
import com.voicebridge.port.out.StorageDeletionQueuePort.QueuedDeletion;
import com.voicebridge.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 재시도 큐의 파일을 오래된 것부터 다시 지운다. 성공하면 큐에서 빼고, 실패하면 시도 횟수를 올려 다음 차례에 다시 시도한다. 저장소 삭제는 이미 없는 파일도 성공으로
 * 처리되므로(멱등) 같은 key를 여러 번 지워도 안전하다. 외부 호출(저장소)을 감싸지 않도록 @Transactional을 두지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageDeletionRetryService implements RetryStorageDeletionUseCase {

  private final StorageDeletionQueuePort deletionQueuePort;
  private final StoragePort storagePort;

  @Override
  public int retryPending() {
    int deleted = 0;
    for (QueuedDeletion item :
        deletionQueuePort.findOldest(StorageDeletionPolicy.RETRY_BATCH_SIZE)) {
      try {
        storagePort.delete(item.storageKey());
        deletionQueuePort.remove(item.storageKey());
        deleted++;
      } catch (RuntimeException e) {
        recordFailure(item, e);
      }
    }
    if (deleted > 0) {
      log.info("[파일 삭제 재시도] {}개 삭제", deleted);
    }
    return deleted;
  }

  private void recordFailure(QueuedDeletion item, RuntimeException cause) {
    try {
      int attempts = deletionQueuePort.recordFailure(item.storageKey());
      if (attempts >= StorageDeletionPolicy.ALERT_AFTER_ATTEMPTS) {
        log.error(
            "[파일 삭제 재시도] {}회 실패, 확인 필요 key={} cause={}",
            attempts,
            item.storageKey(),
            cause.getClass().getSimpleName());
      } else {
        log.warn(
            "[파일 삭제 재시도] 실패 key={} cause={}", item.storageKey(), cause.getClass().getSimpleName());
      }
    } catch (RuntimeException e) {
      log.warn("[파일 삭제 재시도] 실패 기록도 못 함 key={}", item.storageKey(), e);
    }
  }
}
