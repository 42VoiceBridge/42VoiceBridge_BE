package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(DiagnosisSessionPersistenceAdapter.class)
class DiagnosisSessionPersistenceAdapterTest {

  @Autowired private DiagnosisSessionPersistenceAdapter adapter;
  @Autowired private TestEntityManager entityManager;

  private DiagnosisSession save(UUID userId, DiagnosisSessionStatus status) {
    DiagnosisSession session = DiagnosisSession.start(userId, List.of(UUID.randomUUID()));
    if (status != DiagnosisSessionStatus.IN_PROGRESS) {
      session.markAnalyzed();
    }
    if (status == DiagnosisSessionStatus.COMPLETED) {
      session.complete();
    }
    return adapter.save(session);
  }

  @Test
  void 사용자의_집계_대상_세션만_조회한다() {
    UUID userId = UUID.randomUUID();
    save(userId, DiagnosisSessionStatus.IN_PROGRESS);
    DiagnosisSession analyzed = save(userId, DiagnosisSessionStatus.ANALYZED);
    DiagnosisSession completed = save(userId, DiagnosisSessionStatus.COMPLETED);
    save(UUID.randomUUID(), DiagnosisSessionStatus.ANALYZED);
    entityManager.flush();
    entityManager.clear();

    List<DiagnosisSession> found =
        adapter.findByUserIdAndStatusIn(userId, DiagnosisSessionStatus.aggregated());

    assertThat(found)
        .extracting(DiagnosisSession::getId)
        .containsExactlyInAnyOrder(analyzed.getId(), completed.getId());
  }
}
