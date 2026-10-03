package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetRecognitionHistoryUseCase.HistoryPage;
import java.util.List;

public record RecognitionHistoryResponse(
    List<RecognitionResponse> content, int page, int size, long totalElements, int totalPages) {
  public static RecognitionHistoryResponse from(HistoryPage result) {
    return new RecognitionHistoryResponse(
        result.content().stream().map(RecognitionResponse::from).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }
}
