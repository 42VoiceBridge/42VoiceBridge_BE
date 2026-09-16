package com.voicebridge.application;

import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.TokenProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 로그인 방식(자체/카카오)이나 재발급 여부와 무관하게 "토큰을 발급하고 저장한다"는
 * 동작은 하나로 통일한다. LoginService/KakaoLoginService/RefreshTokenService가 공통으로 쓴다.
 */
@Component
@RequiredArgsConstructor
class TokenIssuer {

    private final TokenProviderPort tokenProviderPort;
    private final RefreshTokenStorePort refreshTokenStorePort;

    TokenResult issue(UUID userId) {
        String accessToken = tokenProviderPort.createAccessToken(userId);
        String refreshToken = tokenProviderPort.createRefreshToken(userId);
        refreshTokenStorePort.save(userId, refreshToken);
        return new TokenResult(accessToken, refreshToken, tokenProviderPort.getAccessTokenExpireSeconds());
    }
}
