package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 회원 탈퇴 계약. API 명세서 1.8절 참고. 이메일 회원은 비밀번호를 다시 확인하고, 카카오 회원은 비밀번호 없이 탈퇴한다. 계정과 모든 데이터(음성 데이터, 내 문구,
 * 음성 파일)를 지우고 리프레시 토큰을 무효로 한다.
 */
public interface WithdrawUseCase {

  void withdraw(WithdrawCommand command);

  record WithdrawCommand(UUID userId, String password) {}
}
