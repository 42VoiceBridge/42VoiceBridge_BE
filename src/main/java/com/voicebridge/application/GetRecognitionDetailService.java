package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.GetRecognitionDetailUseCase;
import com.voicebridge.port.in.GetRecognitionHistoryUseCase.RecognitionView;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetRecognitionDetailService implements GetRecognitionDetailUseCase {
  private final RecognitionRepositoryPort recognitionRepositoryPort;

  @Override
  public RecognitionView getDetail(UUID userId, UUID recognitionId) {
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
    return new RecognitionView(
        recognition.getId(),
        recognition.getRecognizedText(),
        recognition.getModelUsed().name(),
        recognition.getConfidence());
  }
}
