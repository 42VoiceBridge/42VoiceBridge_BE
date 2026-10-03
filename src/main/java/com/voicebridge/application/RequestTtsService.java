package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.TtsRequest;
import com.voicebridge.port.in.RequestTtsUseCase;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import com.voicebridge.port.out.TtsRequestRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RequestTtsService implements RequestTtsUseCase {

  private final ConfirmationRepositoryPort confirmationRepositoryPort;
  private final TtsRequestRepositoryPort ttsRequestRepositoryPort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public RequestResult request(UUID userId, UUID confirmationId, UUID idempotencyKey) {
    if (userId == null || confirmationId == null || idempotencyKey == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }

    var confirmation =
        confirmationRepositoryPort
            .findById(confirmationId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!confirmation.isOwnedBy(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    // 무효화된 confirmation으로는 TTS를 요청할 수 없다 — 확인된 문장만 읽는다는 불변조건의 핵심 지점.
    if (!confirmation.isValid()) {
      throw new IllegalStateException("이미 무효화된 확인 내역으로는 TTS를 요청할 수 없습니다.");
    }

    var existing = ttsRequestRepositoryPort.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      TtsRequest existingRequest = existing.get();
      if (!existingRequest.getConfirmationId().equals(confirmationId)) {
        throw new IllegalArgumentException("이미 다른 요청에 사용된 idempotencyKey입니다.");
      }
      return new RequestResult(existingRequest.getId(), existingRequest.getStatus().name());
    }

    var saved = ttsRequestRepositoryPort.save(TtsRequest.create(confirmationId, idempotencyKey));

    // 리스너는 AFTER_COMMIT에 걸려 있어 이 트랜잭션이 커밋된 뒤에 실행된다.
    eventPublisher.publishEvent(
        new TtsRequestedEvent(saved.getId(), confirmation.getConfirmedText()));

    return new RequestResult(saved.getId(), saved.getStatus().name());
  }
}
