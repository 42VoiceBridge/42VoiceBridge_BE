package com.voicebridge.port.out;

import java.util.UUID;

/** "refresh token은 서버에서 검증 가능한 형태로 저장한다" 요구사항을 담당하는 포트. */
public interface RefreshTokenStorePort {

    void save(UUID userId, String refreshToken);

    boolean isValid(UUID userId, String refreshToken);

    void revoke(UUID userId);
}
