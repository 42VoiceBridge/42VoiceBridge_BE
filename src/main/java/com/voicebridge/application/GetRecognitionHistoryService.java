package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.GetRecognitionHistoryUseCase;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetRecognitionHistoryService implements GetRecognitionHistoryUseCase {
  // 페이지 크기 제한은 조회 유스케이스에서 한 번만 검사한다.
  private static final int MAX_PAGE_SIZE = 100;
  private final RecognitionRepositoryPort recognitionRepositoryPort;

  @Override
  public HistoryPage getHistory(UUID userId, int page, int size) {
    if (userId == null || page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    var result = recognitionRepositoryPort.findByUserId(userId, page, size);
    var content =
        result.content().stream()
            .map(
                r ->
                    new RecognitionView(
                        r.getId(),
                        r.getRecognizedText(),
                        r.getModelUsed().name(),
                        r.getConfidence()))
            .toList();
    return new HistoryPage(content, page, size, result.totalElements(), result.totalPages());
  }
}
