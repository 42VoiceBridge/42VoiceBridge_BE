package com.voicebridge.port.in;

import java.util.UUID;

public interface GetMyProfileUseCase {

  MyProfileResult getMyProfile(UUID userId);

  // provider: LOCAL(이메일 회원) 또는 KAKAO. 프론트가 비밀번호 변경·탈퇴 화면을 회원 종류에 맞게 보여 줄 때 쓴다.
  record MyProfileResult(UUID userId, String email, String nickname, String provider) {}
}
