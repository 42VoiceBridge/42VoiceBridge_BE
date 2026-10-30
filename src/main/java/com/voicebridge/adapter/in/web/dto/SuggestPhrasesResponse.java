package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.SuggestPhrasesUseCase.SuggestResult;
import java.util.List;

public record SuggestPhrasesResponse(List<String> candidates) {
  public static SuggestPhrasesResponse from(SuggestResult result) {
    return new SuggestPhrasesResponse(result.candidates());
  }
}
