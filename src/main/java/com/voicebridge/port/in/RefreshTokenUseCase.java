package com.voicebridge.port.in;

public interface RefreshTokenUseCase {

  LoginUseCase.TokenResult refresh(String refreshToken);
}
