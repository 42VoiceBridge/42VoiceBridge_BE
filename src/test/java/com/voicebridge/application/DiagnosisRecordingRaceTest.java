package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.adapter.out.persistence.DiagnosisSessionJpaRepository;
import com.voicebridge.adapter.out.persistence.DiagnosisSessionPersistenceAdapter;
import com.voicebridge.adapter.out.persistence.RecordingJpaRepository;
import com.voicebridge.adapter.out.persistence.RecordingPersistenceAdapter;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.RecordingStatus;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase.UploadCommand;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.support.MySqlContainerTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * 녹음 등록과 세션 분석 완료 판단이 같은 세션에서 겹치는 경합을 실제 MySQL 잠금으로 재현한다. 두 쪽 모두 세션 행을 잠그므로, 늦은 쪽은 먼저 끝난 쪽의 결과를 보고
 * 판단해야 한다. 한쪽은 진짜 빈을 부르고, 다른 쪽은 잠근 채 멈춰 있어야 해서 같은 순서를 테스트가 직접 밟는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  DiagnosisSessionPersistenceAdapter.class,
  RecordingPersistenceAdapter.class,
  DiagnosisRecordingRegistrar.class,
  DiagnosisSessionAnalysisTrigger.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DiagnosisRecordingRaceTest extends MySqlContainerTest {

  @Autowired private DiagnosisRecordingRegistrar registrar;
  @Autowired private DiagnosisSessionAnalysisTrigger trigger;
  @Autowired private DiagnosisSessionRepositoryPort sessionRepository;
  @Autowired private RecordingRepositoryPort recordingRepository;
  @Autowired private DiagnosisSessionJpaRepository sessionJpaRepository;
  @Autowired private RecordingJpaRepository recordingJpaRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final UUID userId = UUID.randomUUID();
  private final UUID sentenceA = UUID.randomUUID();
  private final UUID sentenceB = UUID.randomUUID();
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private DiagnosisSession session;
  private TransactionStatus held;
  private Thread other;

  @BeforeEach
  void setUp() {
    // 두 문장 모두 인식이 끝난 세션. 분석 완료 판단이 새 녹음을 보지 못하면 바로 ANALYZED로 끝낸다.
    session = sessionRepository.save(DiagnosisSession.start(userId, List.of(sentenceA, sentenceB)));
    recordingRepository.save(doneEarlier(sentenceA));
    recordingRepository.save(doneEarlier(sentenceB));
  }

  @AfterEach
  void cleanUp() throws InterruptedException {
    // 테스트가 중간에 실패하면 직접 연 트랜잭션이 잠금을 쥔 채 남는다. 그러면 정리 쿼리와 테이블 삭제가 그 잠금을 끝없이 기다리므로 먼저 푼다.
    if (held != null && !held.isCompleted()) {
      transactionManager.rollback(held);
    }
    if (other != null) {
      other.join(15_000);
    }
    recordingJpaRepository.deleteAllInBatch();
    // 문장 목록 테이블이 세션을 외래키로 가리켜서 deleteAllInBatch()는 쓸 수 없다
    sessionJpaRepository.deleteAll();
  }

  private Recording doneEarlier(UUID sentenceId) {
    return Recording.reconstitute(
        UUID.randomUUID(),
        session.getId(),
        sentenceId,
        userId,
        "recordings/first.wav",
        RecordingStatus.DONE,
        "인식 결과",
        null,
        LocalDateTime.now().minusMinutes(1));
  }

  // 다른 요청이 세션을 잠근 채 멈춰 있는 상태를 만들려고, 트랜잭션을 테스트가 직접 열고 닫는다
  private TransactionStatus beginTransaction() {
    return transactionManager.getTransaction(new DefaultTransactionDefinition());
  }

  @Test
  void 분석_완료_판단이_세션을_잡고_있는_동안_올라온_녹음은_판단이_끝난_뒤_거절된다() throws Exception {
    // 분석 완료 판단: 세션을 잠갔다(DiagnosisSessionAnalysisTrigger의 첫 단계)
    held = beginTransaction();
    DiagnosisSession locked = sessionRepository.findByIdForUpdate(session.getId()).orElseThrow();

    // 그 사이 S3 업로드를 마친 요청이 문장 A를 다시 녹음한 것을 등록하려 한다
    UploadCommand retake =
        new UploadCommand(userId, session.getId(), sentenceA, new byte[] {1}, "retake.wav");
    other =
        startInAnotherThread(
            () -> registrar.register(retake, "recordings/retake.wav", new byte[] {1}), failure);
    awaitLockWait(other, failure);

    locked.markAnalyzed();
    sessionRepository.save(locked);
    transactionManager.commit(held);
    other.join(5_000);

    // 전역 핸들러가 409로 바꾼다. 분석이 끝난 세션에 녹음이 들어가면 이미 계산한 통계의 재료가 바뀐다.
    assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
    assertThat(recordingRepository.findBySessionId(session.getId())).hasSize(2);
  }

  @Test
  void 녹음_등록이_세션을_잡고_있으면_분석_완료_판단은_등록이_끝난_뒤_새_녹음까지_보고_판단한다() throws Exception {
    // 녹음 등록: 세션을 잠그고 문장 A를 다시 녹음한 것을 저장했다. 아직 커밋 전(DiagnosisRecordingRegistrar와 같은 순서)
    held = beginTransaction();
    sessionRepository.findByIdForUpdate(session.getId()).orElseThrow();
    Recording retake =
        Recording.create(session.getId(), sentenceA, userId, "recordings/retake.wav");
    retake.markProcessing();
    recordingRepository.save(retake);

    // 다른 녹음의 인식이 끝나 분석 완료 판단이 불린다
    other =
        startInAnotherThread(
            () -> trigger.onRecordingRecognized(new RecordingRecognizedEvent(session.getId())),
            failure);
    awaitLockWait(other, failure);

    transactionManager.commit(held);
    other.join(5_000);

    // 다시 녹음한 것이 아직 인식 중이므로 세션은 끝나지 않아야 한다
    assertThat(failure.get()).isNull();
    assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStatus())
        .isEqualTo(DiagnosisSessionStatus.IN_PROGRESS);
  }
}
