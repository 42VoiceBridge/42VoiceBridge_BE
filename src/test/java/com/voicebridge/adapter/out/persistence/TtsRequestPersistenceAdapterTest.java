package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import com.voicebridge.common.exception.*;
import com.voicebridge.domain.recognition.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TtsRequestPersistenceAdapter.class)
class TtsRequestPersistenceAdapterTest {
  @Autowired TtsRequestPersistenceAdapter adapter;
  @Autowired TtsRequestJpaRepository repository;
  @Autowired TestEntityManager entityManager;

  @Test
  void TTS_요청을_DB에_저장한다() {
    var ttsRequest = TtsRequest.create(UUID.randomUUID(), UUID.randomUUID());
    var saved = adapter.save(ttsRequest);
    entityManager.clear();

    var row = repository.findById(saved.getId()).orElseThrow();
    assertThat(row.getConfirmationId()).isEqualTo(ttsRequest.getConfirmationId());
    assertThat(row.getIdempotencyKey()).isEqualTo(ttsRequest.getIdempotencyKey());
    assertThat(row.getStatus()).isEqualTo(TtsRequestStatus.PENDING);
    assertThat(row.getAudioUrl()).isNull();
    assertThat(saved).usingRecursiveComparison().isEqualTo(ttsRequest);
  }

  @Test
  void findByIdempotencyKey로_조회한다() {
    UUID idempotencyKey = UUID.randomUUID();
    adapter.save(TtsRequest.create(UUID.randomUUID(), idempotencyKey));
    entityManager.clear();

    assertThat(adapter.findByIdempotencyKey(idempotencyKey)).isPresent();
    assertThat(adapter.findByIdempotencyKey(UUID.randomUUID())).isEmpty();
  }

  @Test
  void 같은_idempotencyKey를_두번_저장하면_유니크_제약_위반으로_예외() {
    UUID idempotencyKey = UUID.randomUUID();
    adapter.save(TtsRequest.create(UUID.randomUUID(), idempotencyKey));
    entityManager.clear();

    var duplicate = TtsRequest.create(UUID.randomUUID(), idempotencyKey);
    assertThatThrownBy(() -> adapter.save(duplicate))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
  }
}
