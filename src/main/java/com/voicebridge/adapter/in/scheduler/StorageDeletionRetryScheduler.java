package com.voicebridge.adapter.in.scheduler;

import com.voicebridge.port.in.RetryStorageDeletionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 지우지 못한 저장소 파일을 주기적으로 다시 지운다. 테스트에서는 꺼 둔다(voicebridge.storage.deletion-retry.enabled). */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "voicebridge.storage.deletion-retry.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class StorageDeletionRetryScheduler {

  private final RetryStorageDeletionUseCase retryStorageDeletionUseCase;

  @Scheduled(
      initialDelayString = "${voicebridge.storage.deletion-retry.delay-ms:600000}",
      fixedDelayString = "${voicebridge.storage.deletion-retry.delay-ms:600000}")
  public void retry() {
    try {
      retryStorageDeletionUseCase.retryPending();
    } catch (RuntimeException e) {
      // DB 장애 등. 다음 주기에 다시 시도한다.
      log.warn("[파일 삭제 재시도] 이번 주기 건너뜀 cause={}", e.getClass().getSimpleName());
    }
  }
}
