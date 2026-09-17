package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.AuthProvider;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.LoginUseCase.TokenResult;
import com.voicebridge.port.out.KakaoUserInfoPort;
import com.voicebridge.port.out.KakaoUserInfoPort.KakaoUserInfo;
import com.voicebridge.port.out.UserRepositoryPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoLoginServiceTest {

  @Mock private KakaoUserInfoPort kakaoUserInfoPort;

  @Mock private UserRepositoryPort userRepositoryPort;

  @Mock private TokenIssuer tokenIssuer;

  private KakaoLoginService service;

  @BeforeEach
  void setUp() {
    service = new KakaoLoginService(kakaoUserInfoPort, userRepositoryPort, tokenIssuer);
  }

  @Test
  void 처음_로그인하는_카카오_사용자는_신규_가입_후_토큰을_발급한다() {
    KakaoUserInfo info = new KakaoUserInfo("kakao-id", "kakao@example.com", "닉네임");
    when(kakaoUserInfoPort.fetchUserInfo("kakao-access-token")).thenReturn(info);
    when(userRepositoryPort.findByProviderAndProviderId(AuthProvider.KAKAO, info.providerId()))
        .thenReturn(Optional.empty());
    when(userRepositoryPort.save(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    TokenResult expected = new TokenResult("access-token", "refresh-token", 3600L);
    when(tokenIssuer.issue(any())).thenReturn(expected);

    TokenResult result = service.loginWithKakao("kakao-access-token");

    assertThat(result).isEqualTo(expected);
    verify(userRepositoryPort).save(any(User.class));
  }

  @Test
  void 이미_가입된_카카오_사용자는_재가입_없이_토큰을_발급한다() {
    KakaoUserInfo info = new KakaoUserInfo("kakao-id", "kakao@example.com", "닉네임");
    User existingUser = User.createFromKakao(info.providerId(), info.email(), info.nickname());
    when(kakaoUserInfoPort.fetchUserInfo("kakao-access-token")).thenReturn(info);
    when(userRepositoryPort.findByProviderAndProviderId(AuthProvider.KAKAO, info.providerId()))
        .thenReturn(Optional.of(existingUser));
    TokenResult expected = new TokenResult("access-token", "refresh-token", 3600L);
    when(tokenIssuer.issue(existingUser.getId())).thenReturn(expected);

    TokenResult result = service.loginWithKakao("kakao-access-token");

    assertThat(result).isEqualTo(expected);
    verify(userRepositoryPort, never()).save(any(User.class));
  }

  @Test
  void 카카오_사용자_정보_조회에_실패하면_KAKAO_AUTH_FAILED_예외를_던진다() {
    when(kakaoUserInfoPort.fetchUserInfo("bad-token"))
        .thenThrow(new RuntimeException("kakao api error"));

    assertThatThrownBy(() -> service.loginWithKakao("bad-token"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.KAKAO_AUTH_FAILED);
  }
}
