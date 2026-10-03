package com.voicebridge.port.in;

import java.util.UUID;

/** 인식 결과 조회 계약. API 명세서 5.3절 참고. 본인 소유 체크는 Recognition.isOwnedBy(). */
public interface GetRecognitionDetailUseCase {

  GetRecognitionHistoryUseCase.RecognitionView getDetail(UUID userId, UUID recognitionId);
}
