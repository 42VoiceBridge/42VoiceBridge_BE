package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.user.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    Optional<UserJpaEntity> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);
}
