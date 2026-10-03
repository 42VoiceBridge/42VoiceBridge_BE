package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.personalization.PersonalizationAdapter;
import com.voicebridge.port.out.PersonalizationAdapterRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersonalizationAdapterPersistenceAdapter
    implements PersonalizationAdapterRepositoryPort {
  private final PersonalizationAdapterJpaRepository repository;

  @Override
  public Optional<PersonalizationAdapter> findActiveByUserId(UUID userId) {
    return repository.findByActiveUserId(userId).map(PersonalizationAdapterJpaEntity::toDomain);
  }
}
