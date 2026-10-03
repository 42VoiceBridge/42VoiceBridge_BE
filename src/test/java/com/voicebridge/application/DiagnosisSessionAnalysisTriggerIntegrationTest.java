package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "커밋된 뒤에, 새 트랜잭션에서" 판단한다는 약속은 Mock으로 증명할 수 없어서 실제 빈과 트랜잭션으로 확인한다. 테스트마다 새 UUID로 세션을 만들어 서로의 데이터에
 * 영향을 주지 않는다.
 */
@SpringBootTest
class DiagnosisSessionAnalysisTriggerIntegrationTest {

  @Autowired DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  @Autowired RecordingRepositoryPort recordingRepositoryPort;
  @Autowired ApplicationEventPublisher eventPublisher;
  @Autowired PlatformTransactionManager transactionManager;

  private TransactionTemplate tx;
  private final UUID sentenceA = UUID.randomUUID();
  private final UUID sentenceB = UUID.randomUUID();
  private DiagnosisSession session;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(transactionManager);
    session =
        diagnosisSessionRepositoryPort.save(
            DiagnosisSession.start(UUID.randomUUID(), List.of(sentenceA, sentenceB)));
  }

  private Recording saveProcessing(UUID sentenceId) {
    Recording recording =
        Recording.create(session.getId(), sentenceId, session.getUserId(), "recordings/a.wav");
    recording.markProcessing();
    return recordingRepositoryPort.save(recording);
  }

  private void saveDone(UUID sentenceId) {
    Recording recording = saveProcessing(sentenceId);
    recording.markProcessed("인식 결과", null);
    recordingRepositoryPort.save(recording);
  }

  private DiagnosisSessionStatus currentStatus() {
    return diagnosisSessionRepositoryPort.findById(session.getId()).orElseThrow().getStatus();
  }

  @Test
  void 마지막_녹음의_인식이_커밋되면_세션이_ANALYZED가_된다() {
    saveDone(sentenceA);
    Recording last = saveProcessing(sentenceB);

    // 인식 핸들러가 하는 일과 같다: 같은 트랜잭션에서 DONE으로 저장하고 이벤트를 발행한다
    tx.executeWithoutResult(
        status -> {
          last.markProcessed("인식 결과", null);
          recordingRepositoryPort.save(last);
          eventPublisher.publishEvent(new RecordingRecognizedEvent(session.getId()));
        });

    assertThat(currentStatus()).isEqualTo(DiagnosisSessionStatus.ANALYZED);
  }

  @Test
  void 인식_트랜잭션이_롤백되면_판단하지_않는다() {
    // 녹음은 이미 모두 DONE이라, 판단이 실행되기만 하면 ANALYZED가 된다
    saveDone(sentenceA);
    saveDone(sentenceB);

    tx.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(new RecordingRecognizedEvent(session.getId()));
          status.setRollbackOnly();
        });

    assertThat(currentStatus()).isEqualTo(DiagnosisSessionStatus.IN_PROGRESS);
  }
}
