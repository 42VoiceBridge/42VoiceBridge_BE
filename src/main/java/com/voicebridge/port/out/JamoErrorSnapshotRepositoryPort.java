package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.JamoErrorSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface JamoErrorSnapshotRepositoryPort {

  Optional<JamoErrorSnapshot> findByUserId(UUID userId);

  /** 사용자당 하나만 두므로, 이미 있으면 새 값으로 통째로 바꾼다. */
  JamoErrorSnapshot save(JamoErrorSnapshot snapshot);
}
