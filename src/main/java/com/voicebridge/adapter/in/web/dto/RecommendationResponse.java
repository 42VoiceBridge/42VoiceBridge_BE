package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.RecommendSentencesUseCase;
import java.util.List;
import java.util.UUID;

public record RecommendationResponse(List<SentenceDto> sentences) {

  public static RecommendationResponse from(RecommendSentencesUseCase.RecommendationResult result) {
    return new RecommendationResponse(
        result.sentences().stream()
            .map(s -> new SentenceDto(s.shownPromptId(), s.promptId(), s.text()))
            .toList());
  }

  public record SentenceDto(UUID shownPromptId, String promptId, String text) {}
}
