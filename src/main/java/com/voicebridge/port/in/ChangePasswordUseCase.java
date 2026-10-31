package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 비밀번호 변경 계약. API 명세서 1.6절 참고. 성공하면 새 토큰 한 쌍을 발급한다 — 리프레시 토큰은 사용자당 하나라 새로 발급하면 다른 기기의 리프레시 토큰은 무효가
 * 된다(그 기기는 액세스 토큰이 만료되면 다시 로그인해야 한다).
 */
public interface ChangePasswordUseCase {

  LoginUseCase.TokenResult changePassword(ChangePasswordCommand command);

  record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {}
}
