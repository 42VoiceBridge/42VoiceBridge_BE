package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.TokenProviderPort;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private TokenProviderPort tokenProviderPort;

  @Mock private RefreshTokenStorePort refreshTokenStorePort;

  @Mock private TokenIssuer tokenIssuer;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    service = new RefreshTokenService(tokenProviderPort, refreshTokenStorePort, tokenIssuer);
  }

  @Test
  void 유효한_리프레시_토큰이면_기존_토큰을_폐기하고_새_토큰을_발급한다() {
    UUID userId = UUID.randomUUID();
    String refreshToken = "valid-refresh-token";
    when(tokenProviderPort.validateAndGetUserId(refreshToken)).thenReturn(userId);
    when(refreshTokenStorePort.isValid(userId, refreshToken)).thenReturn(true);
    TokenResult expected = new TokenResult("new-access-token", "new-refresh-token", 3600L);
    when(tokenIssuer.issue(userId)).thenReturn(expected);

    TokenResult result = service.refresh(refreshToken);

    assertThat(result).isEqualTo(expected);
    verify(refreshTokenStorePort).revoke(userId);
  }

  @Test
  void 토큰_검증에_실패하면_REFRESH_TOKEN_INVALID_예외를_던진다() {
    String malformedToken = "malformed-token";
    when(tokenProviderPort.validateAndGetUserId(malformedToken))
        .thenThrow(new CustomException(ErrorCode.AUTH_TOKEN_EXPIRED));

    assertThatThrownBy(() -> service.refresh(malformedToken))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
  }

  @Test
  void 이미_폐기된_토큰이면_REFRESH_TOKEN_INVALID_예외를_던진다() {
    UUID userId = UUID.randomUUID();
    String revokedToken = "revoked-refresh-token";
    when(tokenProviderPort.validateAndGetUserId(revokedToken)).thenReturn(userId);
    when(refreshTokenStorePort.isValid(userId, revokedToken)).thenReturn(false);

    assertThatThrownBy(() -> service.refresh(revokedToken))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
  }
}
