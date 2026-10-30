package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetPhrasesUseCase.CategoryPhrases;
import java.util.List;
import java.util.UUID;

public record PhraseListResponse(List<CategoryResponse> categories) {

  public record CategoryResponse(String category, String label, List<PhraseResponse> phrases) {}

  public record PhraseResponse(UUID phraseId, String text, boolean custom) {}

  public static PhraseListResponse from(List<CategoryPhrases> result) {
    return new PhraseListResponse(
        result.stream()
            .map(
                group ->
                    new CategoryResponse(
                        group.category(),
                        group.label(),
                        group.phrases().stream()
                            .map(
                                item ->
                                    new PhraseResponse(item.phraseId(), item.text(), item.custom()))
                            .toList()))
            .toList());
  }
}
