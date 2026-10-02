package com.voicebridge.port.out;

public interface KakaoUserInfoPort {

  /** 카카오 인가 코드로 사용자 정보를 가져온다. 코드를 카카오 액세스 토큰으로 바꾸는 과정은 구현체가 맡는다. */
  KakaoUserInfo fetchUserInfo(String authorizationCode);

  record KakaoUserInfo(String providerId, String email, String nickname) {}
}
