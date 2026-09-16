package com.voicebridge.port.out;

import java.util.UUID;

public interface RefreshTokenStorePort {

    void save(UUID userId, String refreshToken);

    boolean isValid(UUID userId, String refreshToken);

    void revoke(UUID userId);
}
