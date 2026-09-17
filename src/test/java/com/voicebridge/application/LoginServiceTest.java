package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.LoginUseCase.LoginCommand;
import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.UserRepositoryPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  @Mock private UserRepositoryPort userRepositoryPort;

  @Mock private PasswordEncoderPort passwordEncoderPort;

  @Mock private TokenIssuer tokenIssuer;

  private LoginService service;

  @BeforeEach
  void setUp() {
    service = new LoginService(userRepositoryPort, passwordEncoderPort, tokenIssuer);
  }

  @Test
  void 이메일과_비밀번호가_맞으면_토큰을_발급한다() {
    User user = User.createLocal("user@example.com", "hashed-password", "닉네임");
    LoginCommand command = new LoginCommand("user@example.com", "raw-password");
    when(userRepositoryPort.findByEmail(command.email())).thenReturn(Optional.of(user));
    when(passwordEncoderPort.matches(command.rawPassword(), user.getPassword())).thenReturn(true);
    TokenResult expected = new TokenResult("access-token", "refresh-token", 3600L);
    when(tokenIssuer.issue(user.getId())).thenReturn(expected);

    TokenResult result = service.login(command);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void 존재하지_않는_이메일이면_AUTH_INVALID_CREDENTIALS_예외를_던진다() {
    LoginCommand command = new LoginCommand("nobody@example.com", "raw-password");
    when(userRepositoryPort.findByEmail(command.email())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(command))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
  }

  @Test
  void 카카오_전용_계정의_이메일이면_AUTH_INVALID_CREDENTIALS_예외를_던진다() {
    User kakaoUser = User.createFromKakao("kakao-id", "kakao@example.com", "닉네임");
    LoginCommand command = new LoginCommand("kakao@example.com", "raw-password");
    when(userRepositoryPort.findByEmail(command.email())).thenReturn(Optional.of(kakaoUser));

    assertThatThrownBy(() -> service.login(command))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
  }

  @Test
  void 비밀번호가_틀리면_AUTH_INVALID_CREDENTIALS_예외를_던진다() {
    User user = User.createLocal("user@example.com", "hashed-password", "닉네임");
    LoginCommand command = new LoginCommand("user@example.com", "wrong-password");
    when(userRepositoryPort.findByEmail(command.email())).thenReturn(Optional.of(user));
    when(passwordEncoderPort.matches(command.rawPassword(), user.getPassword())).thenReturn(false);

    assertThatThrownBy(() -> service.login(command))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
  }
}
