package com.voicebridge.domain.storage;

/** 지우지 못한 저장소 파일의 재시도 정책. */
public final class StorageDeletionPolicy {

  /** 한 번 돌 때 다시 지울 파일 수. 오래된 것부터 꺼낸다. */
  public static final int RETRY_BATCH_SIZE = 100;

  /** 이 횟수를 넘겨 실패하면 계속 재시도하되 error 로그로 운영자에게 알린다. */
  public static final int ALERT_AFTER_ATTEMPTS = 6;

  private StorageDeletionPolicy() {}
}
