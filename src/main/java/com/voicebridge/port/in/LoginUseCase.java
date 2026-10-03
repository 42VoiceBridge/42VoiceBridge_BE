package com.voicebridge.port.in;

public interface LoginUseCase {

  TokenResult login(LoginCommand command);

  record LoginCommand(String email, String rawPassword) {}

  record TokenResult(String accessToken, String refreshToken, long expiresIn) {}
}
