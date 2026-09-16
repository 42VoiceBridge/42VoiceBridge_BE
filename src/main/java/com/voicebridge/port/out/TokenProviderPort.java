package com.voicebridge.port.out;

import java.util.UUID;

public interface TokenProviderPort {

    String createAccessToken(UUID userId);

    String createRefreshToken(UUID userId);

    long getAccessTokenExpireSeconds();

    UUID validateAndGetUserId(String token);
}
