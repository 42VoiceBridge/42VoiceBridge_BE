package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.AuthProvider;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.KakaoLoginUseCase;
import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.out.KakaoUserInfoPort;
import com.voicebridge.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class KakaoLoginService implements KakaoLoginUseCase {

    private final KakaoUserInfoPort kakaoUserInfoPort;
    private final UserRepositoryPort userRepositoryPort;
    private final TokenIssuer tokenIssuer;

    @Override
    public TokenResult loginWithKakao(String kakaoAccessToken) {
        KakaoUserInfoPort.KakaoUserInfo info;
        try {
            info = kakaoUserInfoPort.fetchUserInfo(kakaoAccessToken);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.KAKAO_AUTH_FAILED);
        }

        User user = userRepositoryPort.findByProviderAndProviderId(AuthProvider.KAKAO, info.providerId())
                .orElseGet(() -> userRepositoryPort.save(
                        User.createFromKakao(info.providerId(), info.email(), info.nickname())));

        return tokenIssuer.issue(user.getId());
    }
}
