package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.LoginUseCase;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
  public static TokenResponse from(LoginUseCase.TokenResult result) {
    return new TokenResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
  }
}
