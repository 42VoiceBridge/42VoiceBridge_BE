package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.in.RefreshTokenUseCase;
import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.TokenProviderPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {

  private final TokenProviderPort tokenProviderPort;
  private final RefreshTokenStorePort refreshTokenStorePort;
  private final TokenIssuer tokenIssuer;

  @Override
  public TokenResult refresh(String refreshToken) {
    UUID userId = validate(refreshToken);
    refreshTokenStorePort.revoke(userId);
    return tokenIssuer.issue(userId);
  }

  private UUID validate(String refreshToken) {
    UUID userId;
    try {
      userId = tokenProviderPort.validateAndGetUserId(refreshToken);
    } catch (RuntimeException e) {
      throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    if (!refreshTokenStorePort.isValid(userId, refreshToken)) {
      throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    return userId;
  }
}
