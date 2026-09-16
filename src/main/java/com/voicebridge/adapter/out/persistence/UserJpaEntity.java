package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.user.AuthProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@link com.voicebridge.domain.user.User} 도메인 엔티티의 JPA 매핑 전용 클래스.
 * 도메인 ↔ 엔티티 변환은 {@link UserPersistenceAdapter}에서만 담당한다.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_provider", columnNames = {"provider", "provider_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJpaEntity {

    @Id
    private UUID id;

    private String email;

    private String password;

    private String nickname;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    private String providerId;

    private LocalDateTime createdAt;

    @Builder
    private UserJpaEntity(UUID id, String email, String password, String nickname,
                          AuthProvider provider, String providerId, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = createdAt;
    }
}
