package com.voicebridge.adapter.out.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private final JwtTokenProvider tokenProvider =
      new JwtTokenProvider(
          "test-secret-key-for-jwt-unit-test-please-32-bytes-minimum!", 3600, 1209600);

  @Test
  void 토큰을_발급하면_같은_userId로_검증된다() {
    UUID userId = UUID.randomUUID();

    String accessToken = tokenProvider.createAccessToken(userId);
    UUID decoded = tokenProvider.validateAndGetUserId(accessToken);

    assertThat(decoded).isEqualTo(userId);
  }

  @Test
  void 만료된_토큰을_검증하면_AUTH_TOKEN_EXPIRED_예외를_던진다() {
    JwtTokenProvider expiredTokenProvider =
        new JwtTokenProvider(
            "test-secret-key-for-jwt-unit-test-please-32-bytes-minimum!", -10, 1209600);
    String expiredToken = expiredTokenProvider.createAccessToken(UUID.randomUUID());

    assertThatThrownBy(() -> expiredTokenProvider.validateAndGetUserId(expiredToken))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
  }

  @Test
  void 형식이_깨진_토큰을_검증하면_AUTH_TOKEN_EXPIRED_예외를_던진다() {
    assertThatThrownBy(() -> tokenProvider.validateAndGetUserId("not-a-jwt"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
  }
}
