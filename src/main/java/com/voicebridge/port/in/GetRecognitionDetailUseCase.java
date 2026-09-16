package com.voicebridge.port.in;

import java.util.UUID;

/** 계약만 정의된 상태 — 구현은 백엔드 B 담당. API 명세서 5.3절 참고. 본인 소유 체크는 Recognition.isOwnedBy(). */
public interface GetRecognitionDetailUseCase {

  GetRecognitionHistoryUseCase.RecognitionView getDetail(UUID userId, UUID recognitionId);
}
