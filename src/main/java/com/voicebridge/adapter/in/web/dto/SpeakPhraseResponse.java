package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.SpeakPhraseUseCase.SpeakResult;
import java.util.UUID;

public record SpeakPhraseResponse(UUID phraseId, String audioUrl) {
  public static SpeakPhraseResponse from(SpeakResult result) {
    return new SpeakPhraseResponse(result.phraseId(), result.audioUrl());
  }
}
