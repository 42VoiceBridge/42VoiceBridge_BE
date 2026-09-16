package com.voicebridge.port.in;

public interface LoginUseCase {

    TokenResult login(LoginCommand command);

    record LoginCommand(String email, String rawPassword) {
    }

    /** 자체 로그인/카카오 로그인/토큰 재발급이 모두 이 결과 타입을 공유한다. */
    record TokenResult(String accessToken, String refreshToken, long expiresIn) {
    }
}
