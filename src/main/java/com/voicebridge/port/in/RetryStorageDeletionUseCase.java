package com.voicebridge.port.in;

/** 재시도 큐에 남은 저장소 파일을 다시 지운다. 스케줄러가 주기적으로 부른다. */
public interface RetryStorageDeletionUseCase {

  /** 이번에 지운 파일 수를 돌려준다. */
  int retryPending();
}
