package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.RecommendSentencesUseCase;
import java.util.List;

public record RecommendationResponse(List<SentenceDto> sentences) {

  public static RecommendationResponse from(RecommendSentencesUseCase.RecommendationResult result) {
    return new RecommendationResponse(
        result.sentences().stream().map(s -> new SentenceDto(s.promptId(), s.text())).toList());
  }

  public record SentenceDto(String promptId, String text) {}
}
