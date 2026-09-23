package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.RecordingStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(RecordingPersistenceAdapter.class)
class RecordingPersistenceAdapterTest {

  @Autowired private RecordingPersistenceAdapter adapter;
  @Autowired private TestEntityManager entityManager;

  private Recording newRecording(UUID sessionId, UUID userId) {
    return Recording.create(sessionId, UUID.randomUUID(), userId, "recordings/sample.wav");
  }

  @Test
  void 저장한_녹음의_모든_필드가_그대로_복원된다() {
    UUID sessionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Recording saved = adapter.save(newRecording(sessionId, userId));

    entityManager.flush();
    entityManager.clear();

    Recording found = adapter.findById(saved.getId()).orElseThrow();

    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getSessionId()).isEqualTo(sessionId);
    assertThat(found.getSentenceId()).isEqualTo(saved.getSentenceId());
    assertThat(found.getUserId()).isEqualTo(userId);
    assertThat(found.getS3Path()).isEqualTo("recordings/sample.wav");
    assertThat(found.getStatus()).isEqualTo(RecordingStatus.UPLOADED);
    assertThat(found.getRecognizedText()).isNull();
    assertThat(found.getConfidence()).isNull();
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  void 인식_결과까지_저장하고_복원할_수_있다() {
    Recording recording = newRecording(UUID.randomUUID(), UUID.randomUUID());
    recording.markProcessing();
    recording.markProcessed("오늘 날씨가 좋습니다.", 0.87);

    Recording saved = adapter.save(recording);
    entityManager.flush();
    entityManager.clear();

    Recording found = adapter.findById(saved.getId()).orElseThrow();

    assertThat(found.getStatus()).isEqualTo(RecordingStatus.DONE);
    assertThat(found.getRecognizedText()).isEqualTo("오늘 날씨가 좋습니다.");
    assertThat(found.getConfidence()).isEqualTo(0.87);
  }

  @Test
  void 무음으로_인식된_빈_텍스트도_저장된다() {
    Recording recording = newRecording(UUID.randomUUID(), UUID.randomUUID());
    recording.markProcessing();
    recording.markProcessed("", 0.0);

    Recording saved = adapter.save(recording);
    entityManager.flush();
    entityManager.clear();

    assertThat(adapter.findById(saved.getId()).orElseThrow().getRecognizedText()).isEmpty();
  }

  @Test
  void 실패한_녹음의_상태가_저장된다() {
    Recording recording = newRecording(UUID.randomUUID(), UUID.randomUUID());
    recording.markProcessing();
    recording.markFailed();

    Recording saved = adapter.save(recording);
    entityManager.flush();
    entityManager.clear();

    assertThat(adapter.findById(saved.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordingStatus.FAILED);
  }

  @Test
  void 세션에_속한_녹음만_조회된다() {
    UUID sessionId = UUID.randomUUID();
    UUID otherSessionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    adapter.save(newRecording(sessionId, userId));
    adapter.save(newRecording(sessionId, userId));
    adapter.save(newRecording(otherSessionId, userId));

    entityManager.flush();
    entityManager.clear();

    List<Recording> found = adapter.findBySessionId(sessionId);

    assertThat(found).hasSize(2);
    assertThat(found).allMatch(r -> r.getSessionId().equals(sessionId));
  }

  @Test
  void 존재하지_않는_녹음을_조회하면_비어있다() {
    assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
  }
}
