package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.Confirmation;
import com.voicebridge.port.in.ConfirmRecognitionUseCase;
import com.voicebridge.port.out.ConfirmationRepositoryPort;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfirmRecognitionService implements ConfirmRecognitionUseCase {

  private final RecognitionRepositoryPort recognitionRepositoryPort;
  private final ConfirmationRepositoryPort confirmationRepositoryPort;

  @Override
  public ConfirmResult confirm(UUID userId, UUID recognitionId, String confirmedText) {
    if (userId == null || recognitionId == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }

    var recognition =
        recognitionRepositoryPort
            .findById(recognitionId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!recognition.isOwnedBy(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }

    // 저장(부수효과) 전에 먼저 생성해서 confirmedText 검증을 통과시킨다 — 검증 실패 시 아래 무효화가
    // 먼저 반영돼버리는 반쪽짜리 상태를 만들지 않기 위함이다.
    var newConfirmation = Confirmation.create(recognitionId, userId, confirmedText);

    Optional<Confirmation> previous =
        confirmationRepositoryPort.findValidByRecognitionId(recognitionId);
    if (previous.isPresent()) {
      Confirmation previousConfirmation = previous.get();
      previousConfirmation.invalidate();
      confirmationRepositoryPort.save(previousConfirmation);
    }

    var saved = confirmationRepositoryPort.save(newConfirmation);
    return new ConfirmResult(saved.getId(), saved.getConfirmedText(), saved.getCreatedAt());
  }
}
