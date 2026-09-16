package com.voicebridge.domain.user;

import java.time.LocalDateTime;
import java.util.UUID;

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
