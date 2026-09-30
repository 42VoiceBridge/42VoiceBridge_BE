package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.diagnosis.JamoErrorSnapshot;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import com.voicebridge.support.MySqlContainerTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
 * 같은 사용자의 첫 스냅샷을 두 요청이 동시에 저장하는 경합을 실제 MySQL 잠금으로 재현한다. 테스트 전체를 한 트랜잭션으로 감싸면 두 요청이 겹칠 수 없으므로 테스트
 * 트랜잭션을 끈다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JamoErrorSnapshotPersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JamoErrorSnapshotConcurrentSaveTest extends MySqlContainerTest {

  @Autowired private JamoErrorSnapshotPersistenceAdapter adapter;
  @Autowired private JamoErrorSnapshotJpaRepository jpaRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final UUID userId = UUID.randomUUID();
  private final JamoErrorStat stat = new JamoErrorStat("ㅈ", "INITIAL", 12, 20, 0.6, "OK");
  private TransactionStatus otherRequest;
  private Thread thisRequest;

  @AfterEach
  void cleanUp() throws InterruptedException {
    // 테스트가 중간에 실패하면 직접 연 트랜잭션이 잠금을 쥔 채 남는다. 그러면 정리 쿼리와 테이블 삭제가 그 잠금을 끝없이 기다리므로 먼저 푼다.
    if (otherRequest != null && !otherRequest.isCompleted()) {
      transactionManager.rollback(otherRequest);
    }
    if (thisRequest != null) {
      thisRequest.join(15_000);
    }
    // 자모 목록 테이블이 스냅샷을 외래키로 가리켜서 deleteAllInBatch()는 쓸 수 없다. deleteAll()은 하나씩 읽어 자모 목록부터 지운다.
    jpaRepository.deleteAll();
  }

  @Test
  void 다른_요청이_먼저_만든_첫_스냅샷과_부딪혀도_실패하지_않고_덮어쓴다() throws Exception {
    // 다른 요청: 첫 스냅샷을 INSERT했지만 아직 커밋하지 않았다
    otherRequest = transactionManager.getTransaction(new DefaultTransactionDefinition());
    adapter.save(JamoErrorSnapshot.create(userId, "jamo-err-v1", 10, 1, 5, List.of(stat)));
    jpaRepository.flush();

    // 이 요청: 커밋 전이라 행을 보지 못하고 INSERT한다. 그 INSERT는 먼저 들어간 행의 잠금에 막혀 기다린다.
    AtomicReference<Throwable> failure = new AtomicReference<>();
    thisRequest =
        startInAnotherThread(
            () ->
                adapter.save(
                    JamoErrorSnapshot.create(userId, "jamo-err-v1", 10, 2, 10, List.of(stat))),
            failure);
    // 막히기 전에 커밋하면 이 요청이 행을 보고 처음부터 UPDATE해서 경합이 재현되지 않는다
    awaitLockWait(thisRequest, failure);

    transactionManager.commit(otherRequest);
    thisRequest.join(5_000);

    assertThat(failure.get()).isNull();
    assertThat(adapter.findByUserId(userId).orElseThrow().getSessionsUsed()).isEqualTo(2);
  }
}
