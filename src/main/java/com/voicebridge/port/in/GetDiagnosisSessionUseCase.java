package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 계약만 정의된 상태 — 구현은 백엔드 A 담당. application 패키지에 GetDiagnosisSessionService를 만들어 이 인터페이스를 구현할 것. 본인 소유
 * 세션이 아니면 FORBIDDEN_ACCESS (DiagnosisSession.isOwnedBy() 사용). API 명세서 2.2절 참고.
 */
public interface GetDiagnosisSessionUseCase {

  GetResult getSession(UUID userId, UUID sessionId);

  record GetResult(UUID sessionId, String status) {
    // TODO(백엔드 A): 문장별 녹음 여부 등 API 명세서 2.2 응답 필드를 마저 채워 넣을 것
  }
}
