package com.voicebridge.application;

import com.voicebridge.port.out.StorageDeletionQueuePort;
import com.voicebridge.port.out.StoragePort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DB 행을 지운 뒤 그 파일들을 저장소에서 지운다. 지우지 못한 파일은 재시도 큐에 넣는다(StorageDeletionRetryService가 다시 지운다). 음성 데이터
 * 초기화와 회원 탈퇴가 쓴다. 트랜잭션 밖(DB 커밋 뒤)에서 불러야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StorageFileDeleter {

  private final StoragePort storagePort;
  private final StorageDeletionQueuePort deletionQueuePort;

  /** 지우지 못해 큐에 넣은 파일 수를 돌려준다. key는 무작위 UUID라 로그에 남겨도 개인정보가 없다. */
  int deleteOrQueue(List<String> storageKeys) {
    int queued = 0;
    for (String key : storageKeys) {
      try {
        storagePort.delete(key);
      } catch (RuntimeException e) {
        queued++;
        log.warn("[파일 삭제] 실패해 재시도 큐에 넣음 key={} cause={}", key, e.getClass().getSimpleName());
        enqueue(key);
      }
    }
    return queued;
  }

  private void enqueue(String key) {
    try {
      deletionQueuePort.enqueue(key);
    } catch (RuntimeException e) {
      // 큐에도 못 넣으면 이 로그가 유일한 기록이다. 운영자가 key로 직접 지운다.
      log.error("[파일 삭제] 재시도 큐 기록 실패, 수동 삭제 필요 key={}", key, e);
    }
  }
}
