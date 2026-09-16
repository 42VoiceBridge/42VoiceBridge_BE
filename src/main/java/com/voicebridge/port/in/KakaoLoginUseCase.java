package com.voicebridge.port.in;

public interface KakaoLoginUseCase {

  LoginUseCase.TokenResult loginWithKakao(String kakaoAccessToken);
}
