package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 리프레시 토큰을 서버 검증 가능한 형태(해시)로 저장하는 테이블.
 * 사용자 1명당 최신 리프레시 토큰 1개만 유지한다 — 재로그인/재발급 시 기존 값을 덮어쓴다.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenJpaEntity {

    @Id
    private UUID userId;

    private String tokenHash;

    private LocalDateTime expiresAt;

    @Builder
    private RefreshTokenJpaEntity(UUID userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** Rich Domain 원칙: 값 갱신은 엔티티 스스로 담당한다(Service에서 setter로 건드리지 않는다). */
    public void update(String tokenHash, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
