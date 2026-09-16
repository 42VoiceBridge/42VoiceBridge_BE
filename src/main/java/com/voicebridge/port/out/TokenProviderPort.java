package com.voicebridge.port.out;

import java.util.UUID;

public interface TokenProviderPort {

    String createAccessToken(UUID userId);

    String createRefreshToken(UUID userId);

    long getAccessTokenExpireSeconds();

    /** 토큰이 유효하면 그 안의 userId를 반환하고, 만료/위조 등이면 예외를 던진다. */
    UUID validateAndGetUserId(String token);
}
