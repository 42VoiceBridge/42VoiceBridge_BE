package com.voicebridge.port.in;

import java.util.List;
import java.util.UUID;

/** 인식 결과 조회 계약. API 명세서 5.2절 참고(페이지네이션). */
public interface GetRecognitionHistoryUseCase {

  HistoryPage getHistory(UUID userId, int page, int size);

  record HistoryPage(
      List<RecognitionView> content, int page, int size, long totalElements, int totalPages) {}

  record RecognitionView(
      UUID recognitionId, String recognizedText, String modelUsed, Double confidence) {}
}
