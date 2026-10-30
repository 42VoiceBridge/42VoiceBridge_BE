package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.AddPhraseUseCase.AddedPhrase;
import java.util.UUID;

public record AddPhraseResponse(UUID phraseId, String category, String text, boolean custom) {
  public static AddPhraseResponse from(AddedPhrase result) {
    return new AddPhraseResponse(result.phraseId(), result.category(), result.text(), true);
  }
}
