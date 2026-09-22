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
@Import(RecognitionPersistenceAdapter.class)
class RecognitionPersistenceAdapterTest {
  @Autowired RecognitionPersistenceAdapter adapter;
  @Autowired RecognitionJpaRepository repository;
  @Autowired TestEntityManager entityManager;

  @Test
  void 긴_인식결과와_모델정보를_DB에_저장한다() {
    var recognition =
        Recognition.create(UUID.randomUUID(), "안녕하세요 ".repeat(100), ModelType.PERSONALIZED, 0.8);
    var saved = adapter.save(recognition);
    entityManager.clear();
    var row = repository.findById(saved.getId()).orElseThrow();
    assertThat(row.getUserId()).isEqualTo(recognition.getUserId());
    assertThat(row.getRecognizedText()).isEqualTo(recognition.getRecognizedText());
    assertThat(row.getModelUsed()).isEqualTo(ModelType.PERSONALIZED);
    assertThat(row.getConfidence()).isEqualTo(0.8);
    assertThat(row.getCreatedAt()).isNotNull();
    assertThat(saved).usingRecursiveComparison().isEqualTo(recognition);
  }

  @Test
  void 빈_인식결과도_DB에_저장한다() {
    var saved = adapter.save(Recognition.create(UUID.randomUUID(), "", ModelType.BASE_ADAPTED, 0));
    entityManager.clear();
    assertThat(repository.findById(saved.getId()).orElseThrow().getRecognizedText()).isEmpty();
  }

  @Test
  void 저장_제약_위반을_프로젝트_예외로_번역한다() {
    var invalid = Recognition.create(null, "text", ModelType.BASE_ADAPTED, 0.8);
    assertThatThrownBy(() -> adapter.save(invalid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
  }
}
