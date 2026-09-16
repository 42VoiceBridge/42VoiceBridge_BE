package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.SignUpUseCase;
import java.util.UUID;

public record SignUpResponse(UUID userId, String email, String nickname) {
  public static SignUpResponse from(SignUpUseCase.SignUpResult result) {
    return new SignUpResponse(result.userId(), result.email(), result.nickname());
  }
}
