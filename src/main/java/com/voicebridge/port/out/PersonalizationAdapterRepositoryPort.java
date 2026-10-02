package com.voicebridge.port.out;

import com.voicebridge.domain.personalization.PersonalizationAdapter;
import java.util.Optional;
import java.util.UUID;

public interface PersonalizationAdapterRepositoryPort {
  Optional<PersonalizationAdapter> findActiveByUserId(UUID userId);
}
