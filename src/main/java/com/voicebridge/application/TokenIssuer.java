package com.voicebridge.application;

import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.TokenProviderPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TokenIssuer {

  private final TokenProviderPort tokenProviderPort;
  private final RefreshTokenStorePort refreshTokenStorePort;

  TokenResult issue(UUID userId) {
    String accessToken = tokenProviderPort.createAccessToken(userId);
    String refreshToken = tokenProviderPort.createRefreshToken(userId);
    refreshTokenStorePort.save(userId, refreshToken);
    return new TokenResult(
        accessToken, refreshToken, tokenProviderPort.getAccessTokenExpireSeconds());
  }
}
