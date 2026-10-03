package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.LoginUseCase;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoginService implements LoginUseCase {

  private final UserRepositoryPort userRepositoryPort;
  private final PasswordEncoderPort passwordEncoderPort;
  private final TokenIssuer tokenIssuer;

  @Override
  public TokenResult login(LoginCommand command) {
    User user =
        userRepositoryPort
            .findByEmail(command.email())
            .filter(User::isLocalUser)
            .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    if (!passwordEncoderPort.matches(command.rawPassword(), user.getPassword())) {
      throw new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    return tokenIssuer.issue(user.getId());
  }
}
