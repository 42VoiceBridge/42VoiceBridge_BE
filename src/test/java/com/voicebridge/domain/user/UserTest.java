package com.voicebridge.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void 로컬_사용자를_생성하면_provider가_LOCAL이고_providerId는_없다() {
    User user = User.createLocal("test@voicebridge.com", "hashed-password", "테스터");

    assertThat(user.isLocalUser()).isTrue();
    assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(user.getProviderId()).isNull();
    assertThat(user.getId()).isNotNull();
  }

  @Test
  void 카카오_사용자를_생성하면_provider가_KAKAO이고_password는_없다() {
    User user = User.createFromKakao("kakao-12345", "kakao@example.com", "카카오유저");

    assertThat(user.isLocalUser()).isFalse();
    assertThat(user.getProvider()).isEqualTo(AuthProvider.KAKAO);
    assertThat(user.getPassword()).isNull();
  }

  @Test
  void 로컬_사용자는_비밀번호를_바꿀_수_있다() {
    User user = User.createLocal("test@voicebridge.com", "old-hash", "테스터");

    user.changePassword("new-hash");

    assertThat(user.getPassword()).isEqualTo("new-hash");
  }

  @Test
  void 카카오_사용자는_비밀번호를_바꿀_수_없다() {
    User user = User.createFromKakao("kakao-12345", "kakao@example.com", "카카오유저");

    assertThatThrownBy(() -> user.changePassword("new-hash"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(user.getPassword()).isNull();
  }

  @Test
  void 새_비밀번호는_8자_이상이어야_한다() {
    User.checkNewPassword("12345678");

    assertThatThrownBy(() -> User.checkNewPassword("1234567"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("비밀번호는 8자 이상이어야 합니다.");
    assertThatThrownBy(() -> User.checkNewPassword("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> User.checkNewPassword(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
