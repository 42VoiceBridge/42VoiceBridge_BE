package com.voicebridge.adapter.out.auth;

import com.voicebridge.adapter.out.persistence.RefreshTokenJpaEntity;
import com.voicebridge.adapter.out.persistence.RefreshTokenJpaRepository;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.RefreshTokenStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenStoreAdapter implements RefreshTokenStorePort {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final PasswordEncoderPort passwordEncoderPort;

    @Value("${voicebridge.jwt.refresh-token-expire-seconds}")
    private long refreshTokenExpireSeconds;

    @Override
    @Transactional
    public void save(UUID userId, String refreshToken) {
        String tokenHash = passwordEncoderPort.encode(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpireSeconds);

        refreshTokenJpaRepository.findById(userId)
                .ifPresentOrElse(
                        existing -> existing.update(tokenHash, expiresAt),
                        () -> refreshTokenJpaRepository.save(
                                RefreshTokenJpaEntity.builder()
                                        .userId(userId)
                                        .tokenHash(tokenHash)
                                        .expiresAt(expiresAt)
                                        .build())
                );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValid(UUID userId, String refreshToken) {
        return refreshTokenJpaRepository.findById(userId)
                .filter(entity -> entity.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(entity -> passwordEncoderPort.matches(refreshToken, entity.getTokenHash()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void revoke(UUID userId) {
        refreshTokenJpaRepository.deleteById(userId);
    }
}
