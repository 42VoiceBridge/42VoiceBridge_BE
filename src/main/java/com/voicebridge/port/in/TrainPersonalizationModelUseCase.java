package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 계약만 정의된 상태 — 구현은 백엔드 B 담당. API 명세서 4.2절 참고. 최소 녹음 개수 미충족 시 INSUFFICIENT_RECORDINGS(422), 이미 진행중인
 * job이 있으면 INVALID_STATE_TRANSITION(409) —
 * PersonalizationJobRepositoryPort.findInProgressByUserId()로 확인.
 */
public interface TrainPersonalizationModelUseCase {

  TrainResult train(UUID userId);

  record TrainResult(UUID jobId, String status) {}
}
