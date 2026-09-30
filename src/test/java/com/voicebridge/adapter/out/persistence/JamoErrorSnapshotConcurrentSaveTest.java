package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.diagnosis.JamoErrorSnapshot;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 같은 사용자의 첫 스냅샷을 두 요청이 동시에 저장하는 경합을 실제 DB 잠금으로 재현한다. 테스트 전체를 한 트랜잭션으로 감싸면 두 요청이 겹칠 수 없으므로 테스트 트랜잭션을
 * 끈다.
 *
 * <p>H2가 아니라 MySQL로 돌리는 이유: MySQL은 늦은 INSERT가 먼저 들어간 행의 잠금을 기다렸다가 기본키 충돌로 실패하는데, H2는 기다리지 않고 다른 종류의
 * 예외(동시 수정)로 실패한다. H2로 검증하면 운영에서 일어나지 않는 실패를 막는 코드를 짜게 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JamoErrorSnapshotPersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JamoErrorSnapshotConcurrentSaveTest {

  @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
  }

  @Autowired private JamoErrorSnapshotPersistenceAdapter adapter;
  @Autowired private JamoErrorSnapshotJpaRepository jpaRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final UUID userId = UUID.randomUUID();
  private final JamoErrorStat stat = new JamoErrorStat("ㅈ", "INITIAL", 12, 20, 0.6, "OK");

  @AfterEach
  void cleanUp() {
    // 자모 목록 테이블이 스냅샷을 외래키로 가리켜서 deleteAllInBatch()는 쓸 수 없다. deleteAll()은 하나씩 읽어 자모 목록부터 지운다.
    jpaRepository.deleteAll();
  }

  @Test
  void 다른_요청이_먼저_만든_첫_스냅샷과_부딪혀도_실패하지_않고_덮어쓴다() throws Exception {
    // 다른 요청: 첫 스냅샷을 INSERT했지만 아직 커밋하지 않았다
    TransactionStatus otherRequest =
        transactionManager.getTransaction(new DefaultTransactionDefinition());
    adapter.save(JamoErrorSnapshot.create(userId, "jamo-err-v1", 10, 1, 5, List.of(stat)));
    jpaRepository.flush();

    // 이 요청: 커밋 전이라 행을 보지 못하고 INSERT한다. 그 INSERT는 먼저 들어간 행의 잠금에 막혀 기다린다.
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thisRequest =
        new Thread(
            () -> {
              try {
                adapter.save(
                    JamoErrorSnapshot.create(userId, "jamo-err-v1", 10, 2, 10, List.of(stat)));
              } catch (Throwable e) {
                failure.set(e);
              }
            });
    thisRequest.start();
    // 막히기 전에 커밋하면 이 요청이 행을 보고 처음부터 UPDATE해서 경합이 재현되지 않는다
    awaitLockWait(thisRequest, failure);

    transactionManager.commit(otherRequest);
    thisRequest.join(5_000);

    assertThat(failure.get()).isNull();
    assertThat(adapter.findByUserId(userId).orElseThrow().getSessionsUsed()).isEqualTo(2);
  }

  /**
   * 잠금을 기다리는 건 DB 서버라서 자바 스레드 상태로는 알 수 없다(응답을 기다리는 동안에도 RUNNABLE). MySQL에 잠금 대기 중인 트랜잭션이 있는지 직접
   * 묻는다. 이 조회에는 PROCESS 권한이 필요해 root로 붙는다.
   */
  private static void awaitLockWait(Thread thread, AtomicReference<Throwable> failure)
      throws Exception {
    try (Connection root =
            DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
        PreparedStatement lockWaits =
            root.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state = 'LOCK WAIT'")) {
      long deadline = System.currentTimeMillis() + 5_000;
      while (true) {
        try (ResultSet result = lockWaits.executeQuery()) {
          result.next();
          if (result.getInt(1) > 0) {
            return;
          }
        }
        if (!thread.isAlive() || System.currentTimeMillis() > deadline) {
          throw new AssertionError("저장 요청이 잠금 대기에 들어가지 않았습니다", failure.get());
        }
        Thread.sleep(10);
      }
    }
  }
}
