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
@Import(ConfirmationPersistenceAdapter.class)
class ConfirmationPersistenceAdapterTest {
  @Autowired ConfirmationPersistenceAdapter adapter;
  @Autowired ConfirmationJpaRepository repository;
  @Autowired TestEntityManager entityManager;

  @Test
  void 확인_내역을_DB에_저장한다() {
    var confirmation = Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), "물 좀 주세요");
    var saved = adapter.save(confirmation);
    entityManager.clear();

    var row = repository.findById(saved.getId()).orElseThrow();
    assertThat(row.getRecognitionId()).isEqualTo(confirmation.getRecognitionId());
    assertThat(row.getUserId()).isEqualTo(confirmation.getUserId());
    assertThat(row.getConfirmedText()).isEqualTo("물 좀 주세요");
    assertThat(row.isValid()).isTrue();
    assertThat(saved).usingRecursiveComparison().isEqualTo(confirmation);
  }

  @Test
  void findValidByRecognitionId는_유효한_확인만_반환한다() {
    UUID recognitionId = UUID.randomUUID();
    var confirmation = Confirmation.create(recognitionId, UUID.randomUUID(), "물 좀 주세요");
    adapter.save(confirmation);
    entityManager.clear();

    assertThat(adapter.findValidByRecognitionId(recognitionId)).isPresent();

    var toInvalidate = adapter.findValidByRecognitionId(recognitionId).orElseThrow();
    toInvalidate.invalidate();
    adapter.save(toInvalidate);
    entityManager.clear();

    assertThat(adapter.findValidByRecognitionId(recognitionId)).isEmpty();
  }

  @Test
  void 저장_제약_위반을_프로젝트_예외로_번역한다() {
    var invalid = Confirmation.create(UUID.randomUUID(), null, "물 좀 주세요");
    assertThatThrownBy(() -> adapter.save(invalid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
  }
}
