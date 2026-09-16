package com.voicebridge.domain.user;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 도메인 엔티티. 프레임워크 의존성이 전혀 없다.
 *
 * <p>생성 방법을 팩토리 메서드로 제한해 "이메일/비밀번호로 가입" vs "카카오로 가입"
 * 두 가지 경로만 존재하도록 강제한다(SSOT: 생성 규칙은 여기서만 정의).
 */
public class User {

    private final UUID id;
    private String email;
    private String password;
    private String nickname;
    private final AuthProvider provider;
    private final String providerId;
    private final LocalDateTime createdAt;

    private User(UUID id, String email, String password, String nickname,
                  AuthProvider provider, String providerId, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = createdAt;
    }

    public static User createLocal(String email, String hashedPassword, String nickname) {
        return new User(UUID.randomUUID(), email, hashedPassword, nickname,
                AuthProvider.LOCAL, null, LocalDateTime.now());
    }

    public static User createFromKakao(String providerId, String email, String nickname) {
        return new User(UUID.randomUUID(), email, null, nickname,
                AuthProvider.KAKAO, providerId, LocalDateTime.now());
    }

    /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
    public static User reconstitute(UUID id, String email, String password, String nickname,
                                     AuthProvider provider, String providerId, LocalDateTime createdAt) {
        return new User(id, email, password, nickname, provider, providerId, createdAt);
    }

    public boolean isLocalUser() {
        return provider == AuthProvider.LOCAL;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
