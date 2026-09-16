package com.voicebridge.domain.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
