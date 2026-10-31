package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.ChangePasswordUseCase;
import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangePasswordService implements ChangePasswordUseCase {

  private final UserRepositoryPort userRepositoryPort;
  private final PasswordEncoderPort passwordEncoderPort;
  private final TokenIssuer tokenIssuer;

  @Override
  public TokenResult changePassword(ChangePasswordCommand command) {
    User user =
        userRepositoryPort
            .findById(command.userId())
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!user.isLocalUser()) {
      throw new CustomException(ErrorCode.PASSWORD_NOT_SUPPORTED);
    }
    if (command.currentPassword() == null || command.currentPassword().isBlank()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "현재 비밀번호를 입력해 주세요.");
    }
    if (!passwordEncoderPort.matches(command.currentPassword(), user.getPassword())) {
      throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
    }
    User.checkNewPassword(command.newPassword());
    if (passwordEncoderPort.matches(command.newPassword(), user.getPassword())) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "현재 비밀번호와 다른 비밀번호를 입력해 주세요.");
    }

    user.changePassword(passwordEncoderPort.encode(command.newPassword()));
    userRepositoryPort.save(user);
    // 리프레시 토큰은 사용자당 하나다. 새로 발급하면서 이전 토큰(다른 기기 포함)을 덮어써 무효로 만든다.
    return tokenIssuer.issue(user.getId());
  }
}
