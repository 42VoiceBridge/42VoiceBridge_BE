package com.voicebridge.port.out;

import com.voicebridge.domain.personalization.PersonalizationJob;
import java.util.Optional;
import java.util.UUID;

public interface PersonalizationJobRepositoryPort {

  PersonalizationJob save(PersonalizationJob job);

  Optional<PersonalizationJob> findById(UUID id);

  Optional<PersonalizationJob> findLatestCompletedByUserId(UUID userId);

  Optional<PersonalizationJob> findInProgressByUserId(UUID userId);
}
