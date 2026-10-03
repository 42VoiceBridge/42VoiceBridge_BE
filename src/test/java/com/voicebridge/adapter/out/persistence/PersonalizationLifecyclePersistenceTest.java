package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voicebridge.domain.personalization.PersonalizationAdapter;
import com.voicebridge.domain.personalization.PersonalizationAdapterStatus;
import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.domain.personalization.PersonalizationRecording;
import com.voicebridge.port.out.PersonalizationAdapterRepositoryPort;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import com.voicebridge.port.out.PersonalizationRecordingRepositoryPort;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PersonalizationLifecyclePersistenceTest {
  @Autowired PersonalizationJobRepositoryPort jobs;
  @Autowired PersonalizationAdapterRepositoryPort adapters;
  @Autowired PersonalizationAdapterJpaRepository adapterJpa;
  @Autowired PersonalizationRecordingRepositoryPort recordings;
  @Autowired EntityManager entityManager;

  @Test
  void pending_작업도_활성_작업으로_조회하고_완료된_작업은_제외한다() {
    UUID userId = UUID.randomUUID();
    PersonalizationJob job = PersonalizationJob.create(userId, 5);
    jobs.save(job);
    entityManager.flush();
    entityManager.clear();
    assertThat(jobs.findActiveByUserId(userId))
        .get()
        .extracting(PersonalizationJob::getId)
        .isEqualTo(job.getId());

    job.markInProgress();
    job.complete("v1", "artifact");
    jobs.save(job);
    entityManager.flush();
    entityManager.clear();
    assertThat(jobs.findActiveByUserId(userId)).isEmpty();
  }

  @Test
  void 동일_사용자의_두_활성_작업은_DB에서_거부한다() {
    UUID userId = UUID.randomUUID();
    jobs.save(PersonalizationJob.create(userId, 5));
    entityManager.flush();
    jobs.save(PersonalizationJob.create(userId, 5));
    assertThatThrownBy(entityManager::flush).isInstanceOf(RuntimeException.class);
  }

  @Test
  void 후보_어댑터는_활성_모델이_아니고_활성_어댑터만_조회된다() {
    UUID userId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();
    UUID jobId = UUID.randomUUID();
    adapterJpa.save(
        new PersonalizationAdapterJpaEntity(
            new PersonalizationAdapter(
                UUID.randomUUID(),
                userId,
                jobId,
                PersonalizationAdapterStatus.CANDIDATE,
                "v1",
                "base",
                "hash",
                5,
                now,
                null)));
    entityManager.flush();
    assertThat(adapters.findActiveByUserId(userId)).isEmpty();

    PersonalizationAdapter active =
        new PersonalizationAdapter(
            UUID.randomUUID(),
            userId,
            jobId,
            PersonalizationAdapterStatus.ACTIVE,
            "v1",
            "base",
            "hash",
            5,
            now,
            now);
    adapterJpa.save(new PersonalizationAdapterJpaEntity(active));
    entityManager.flush();
    entityManager.clear();
    PersonalizationAdapter loaded = adapters.findActiveByUserId(userId).orElseThrow();
    assertThat(loaded.id()).isEqualTo(active.id());
    assertThat(loaded.status()).isEqualTo(PersonalizationAdapterStatus.ACTIVE);
    assertThat(loaded.trainingRecordingCount()).isEqualTo(5);
  }

  @Test
  void 녹음_후보_조회는_검토되지_않은_동의_녹음을_제외한다() {
    UUID userId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();
    PersonalizationRecording reviewed = recording(userId, now, true);
    PersonalizationRecording unreviewed = recording(userId, now, false);
    recordings.prepare(reviewed);
    recordings.prepare(unreviewed);
    entityManager.clear();

    assertThat(recordings.findTrainingCandidates(userId, now.plusMinutes(1), 30))
        .extracting(PersonalizationRecording::id)
        .containsExactly(reviewed.id());
  }

  @Test
  void expiryQueryKeepsRecentRecordingsAndDomainStatusRoundTrips() {
    LocalDateTime now = LocalDateTime.now();
    var recent = recording(UUID.randomUUID(), now.minusDays(29), true);
    var expired = recording(UUID.randomUUID(), now.minusDays(31), true);
    recordings.prepare(recent);
    recordings.prepare(expired);
    entityManager.clear();
    assertThat(recordings.findExpiredUploaded(now.minusDays(30)))
        .extracting(PersonalizationRecording::id)
        .contains(expired.id())
        .doesNotContain(recent.id());
    assertThat(recordings.findById(recent.id()).orElseThrow().status())
        .isEqualTo(com.voicebridge.domain.personalization.PersonalizationRecordingStatus.UPLOADED);
  }

  private PersonalizationRecording recording(UUID userId, LocalDateTime now, boolean reviewed) {
    return new PersonalizationRecording(
        UUID.randomUUID(),
        userId,
        UUID.randomUUID(),
        "prompt",
        "제안 문장",
        "personalization/key.wav",
        true,
        "consent-v1",
        now,
        "wav",
        "pcm_s16le",
        16000,
        1,
        16000,
        "source",
        "wav",
        "normalize-v1",
        com.voicebridge.domain.personalization.PersonalizationRecordingStatus.UPLOADED,
        now,
        reviewed ? "pool-v1" : null,
        reviewed ? "실제 발화" : null,
        reviewed ? "review-v1" : null,
        reviewed ? UUID.randomUUID() : null,
        reviewed ? now : null);
  }
}
