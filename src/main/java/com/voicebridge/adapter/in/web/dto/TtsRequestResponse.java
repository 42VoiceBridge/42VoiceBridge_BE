package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.RequestTtsUseCase.RequestResult;
import java.util.UUID;

public record TtsRequestResponse(UUID ttsId, String status) {
  public static TtsRequestResponse from(RequestResult result) {
    return new TtsRequestResponse(result.ttsId(), result.status());
  }
}
