package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetTtsStatusUseCase.TtsStatusResult;
import java.util.UUID;

public record TtsStatusResponse(UUID ttsId, String status, String audioUrl) {
  public static TtsStatusResponse from(TtsStatusResult result) {
    return new TtsStatusResponse(result.ttsId(), result.status(), result.audioUrl());
  }
}
