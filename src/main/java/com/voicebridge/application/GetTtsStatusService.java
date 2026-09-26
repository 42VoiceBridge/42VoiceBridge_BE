package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.GetTtsStatusUseCase;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import com.voicebridge.port.out.TtsRequestRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TtsRequest 자체는 사용자를 직접 들고 있지 않아(비정규화하지 않음), 연결된 confirmation을 거쳐 소유권을 확인한다 — 다른 조회 유스케이스와 동일하게 본인
 * 데이터만 조회 가능해야 한다는 원칙(CLAUDE.md 9장)을 따른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTtsStatusService implements GetTtsStatusUseCase {

  private final TtsRequestRepositoryPort ttsRequestRepositoryPort;
  private final ConfirmationRepositoryPort confirmationRepositoryPort;

  @Override
  public TtsStatusResult getStatus(UUID userId, UUID ttsId) {
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

    return new TtsStatusResult(
        ttsRequest.getId(), ttsRequest.getStatus().name(), ttsRequest.getAudioUrl());
  }
}
