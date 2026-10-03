package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.AuthProvider;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.out.UserRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

  private final UserJpaRepository userJpaRepository;

  @Override
  public User save(User user) {
    try {
      UserJpaEntity saved = userJpaRepository.save(toEntity(user));
      return toDomain(saved);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
  }

  @Override
  public Optional<User> findById(UUID id) {
    return userJpaRepository.findById(id).map(UserPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return userJpaRepository.findByEmail(email).map(UserPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId) {
    return userJpaRepository
        .findByProviderAndProviderId(provider, providerId)
        .map(UserPersistenceAdapter::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return userJpaRepository.existsByEmail(email);
  }

  private static UserJpaEntity toEntity(User user) {
    return UserJpaEntity.builder()
        .id(user.getId())
        .email(user.getEmail())
        .password(user.getPassword())
        .nickname(user.getNickname())
        .provider(user.getProvider())
        .providerId(user.getProviderId())
        .createdAt(user.getCreatedAt())
        .build();
  }

  private static User toDomain(UserJpaEntity entity) {
    return User.reconstitute(
        entity.getId(),
        entity.getEmail(),
        entity.getPassword(),
        entity.getNickname(),
        entity.getProvider(),
        entity.getProviderId(),
        entity.getCreatedAt());
  }
}
