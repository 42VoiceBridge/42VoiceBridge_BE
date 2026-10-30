package com.voicebridge.port.out;

import java.time.Duration;
import java.util.UUID;

/** 사용자별 호출 상한. 외부 유료 API(LLM, STT) 앞에서 비용·남용을 막는다. 상한값은 도메인 정책을 호출하는 쪽이 넘긴다. */
public interface CallRateLimitPort {

  /** 이번 호출을 허용하면 true. window마다 횟수가 처음부터 다시 세어진다. feature는 기능 이름(예: "suggestion")이다. */
  boolean tryAcquire(UUID userId, String feature, int limit, Duration window);
}
