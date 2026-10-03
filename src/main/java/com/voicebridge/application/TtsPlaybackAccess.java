package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.TtsRequest;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import com.voicebridge.port.out.TtsRequestRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** TTS 조회·재생에서 공유하는 소유권 및 confirmation 유효성 검사. */
@Component
@RequiredArgsConstructor
public class TtsPlaybackAccess {
  private final TtsRequestRepositoryPort ttsRequestRepositoryPort;
  private final ConfirmationRepositoryPort confirmationRepositoryPort;

  TtsRequest findAuthorizedRequest(UUID userId, UUID ttsId) {
    if (userId == null || ttsId == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }

    var ttsRequest =
        ttsRequestRepositoryPort
            .findById(ttsId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    var confirmation =
        confirmationRepositoryPort
            .findById(ttsRequest.getConfirmationId())
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!confirmation.isOwnedBy(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }

    if (!confirmation.isValid()) {
      throw new CustomException(ErrorCode.INVALID_STATE_TRANSITION, "무효화된 확인 내역의 TTS는 재생할 수 없습니다.");
    }
    return ttsRequest;
  }
}
