package com.voicebridge.port.out;

import java.util.List;

/** 지우지 못한 저장소 파일 key의 재시도 큐. 실패는 CustomException으로 번역해 던진다. */
public interface StorageDeletionQueuePort {

  /** 큐에 넣는다. 이미 있으면 그대로 둔다. */
  void enqueue(String storageKey);

  /** 오래된 것부터 최대 limit개를 돌려준다. */
  List<QueuedDeletion> findOldest(int limit);

  void remove(String storageKey);

  /** 이번 재시도도 실패했음을 기록하고, 지금까지의 시도 횟수를 돌려준다. */
  int recordFailure(String storageKey);

  record QueuedDeletion(String storageKey, int attempts) {}
}
