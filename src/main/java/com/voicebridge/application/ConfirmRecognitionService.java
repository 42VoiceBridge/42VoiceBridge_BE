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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
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

    // 저장(부수효과) 전에 먼저 생성해서 confirmedText 검증부터 통과시킨다 — 아래 두 save()는
    // 클래스 레벨 @Transactional로 하나의 트랜잭션에 묶여 있어 둘 중 하나만 실패해도 함께
    // 롤백되지만, 애초에 검증 실패로 트랜잭션을 열 필요조차 없게 만드는 게 더 낫다.
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
