package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.SignUpUseCase.SignUpCommand;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

  @Mock private UserRepositoryPort userRepositoryPort;

  @Mock private PasswordEncoderPort passwordEncoderPort;

  private SignUpService service;

  @BeforeEach
  void setUp() {
    service = new SignUpService(userRepositoryPort, passwordEncoderPort);
  }

  @Test
  void 이메일이_중복되지_않으면_회원가입에_성공한다() {
    SignUpCommand command = new SignUpCommand("new@example.com", "password123", "닉네임");
    when(userRepositoryPort.existsByEmail(command.email())).thenReturn(false);
    when(passwordEncoderPort.encode(command.rawPassword())).thenReturn("hashed-password");
    when(userRepositoryPort.save(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.signUp(command);

    assertThat(result.email()).isEqualTo(command.email());
    assertThat(result.nickname()).isEqualTo(command.nickname());
  }

  @Test
  void 이미_존재하는_이메일이면_EMAIL_ALREADY_EXISTS_예외를_던진다() {
    SignUpCommand command = new SignUpCommand("dup@example.com", "password123", "닉네임");
    when(userRepositoryPort.existsByEmail(command.email())).thenReturn(true);

    assertThatThrownBy(() -> service.signUp(command))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
  }
}
