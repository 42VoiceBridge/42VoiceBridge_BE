package com.voicebridge.port.out;

public interface KakaoUserInfoPort {

  KakaoUserInfo fetchUserInfo(String kakaoAccessToken);

  record KakaoUserInfo(String providerId, String email, String nickname) {}
}
