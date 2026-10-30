package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.SpeakPhraseUseCase.SpeakResult;
import java.util.UUID;

public record SpeakPhraseResponse(UUID id, String audioUrl) {
  public static SpeakPhraseResponse from(SpeakResult result) {
    return new SpeakPhraseResponse(result.id(), result.audioUrl());
  }
}
