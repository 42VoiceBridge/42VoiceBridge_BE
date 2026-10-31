package com.voicebridge.domain.user;

import java.time.LocalDateTime;
import java.util.UUID;

public class User {

  /** 새 비밀번호 최소 길이(글자 수). 비밀번호 변경에서 쓴다. */
  public static final int MIN_PASSWORD_LENGTH = 8;

  private final UUID id;
  private String email;
  private String password;
  private String nickname;
  private final AuthProvider provider;
  private final String providerId;
  private final LocalDateTime createdAt;

  private User(
      UUID id,
      String email,
      String password,
      String nickname,
      AuthProvider provider,
      String providerId,
      LocalDateTime createdAt) {
    this.id = id;
    this.email = email;
    this.password = password;
    this.nickname = nickname;
    this.provider = provider;
    this.providerId = providerId;
    this.createdAt = createdAt;
  }

  public static User createLocal(String email, String hashedPassword, String nickname) {
    return new User(
        UUID.randomUUID(),
        email,
        hashedPassword,
        nickname,
        AuthProvider.LOCAL,
        null,
        LocalDateTime.now());
  }

  public static User createFromKakao(String providerId, String email, String nickname) {
    return new User(
        UUID.randomUUID(),
        email,
        null,
        nickname,
        AuthProvider.KAKAO,
        providerId,
        LocalDateTime.now());
  }

  public static User reconstitute(
      UUID id,
      String email,
      String password,
      String nickname,
      AuthProvider provider,
      String providerId,
      LocalDateTime createdAt) {
    return new User(id, email, password, nickname, provider, providerId, createdAt);
  }

  public boolean isLocalUser() {
    return provider == AuthProvider.LOCAL;
  }

  /** 새 비밀번호 규칙을 확인한다. 맞지 않으면 사용자에게 보여 줄 메시지와 함께 IllegalArgumentException을 던진다(400). */
  public static void checkNewPassword(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new IllegalArgumentException("새 비밀번호를 입력해 주세요.");
    }
    if (rawPassword.codePointCount(0, rawPassword.length()) < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
    }
  }

  /** 해시한 새 비밀번호로 바꾼다. 카카오 계정은 비밀번호가 없어 바꿀 수 없다. */
  public void changePassword(String hashedPassword) {
    if (!isLocalUser()) {
      throw new IllegalStateException("카카오 계정은 비밀번호를 사용하지 않습니다.");
    }
    if (hashedPassword == null || hashedPassword.isBlank()) {
      throw new IllegalArgumentException("새 비밀번호가 필요합니다.");
    }
    this.password = hashedPassword;
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
