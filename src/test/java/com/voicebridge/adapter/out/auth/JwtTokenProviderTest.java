package com.voicebridge.adapter.out.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
}
