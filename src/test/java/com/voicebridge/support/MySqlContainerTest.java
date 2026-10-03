package com.voicebridge.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 운영 DB(MySQL)의 잠금 동작을 봐야 하는 테스트의 공통 설정. H2는 잠금 경합에서 MySQL과 다르게 동작해서(기다리지 않고 다른 예외로 실패), 경합 테스트는 실제
 * MySQL로 돌린다.
 *
 * <p>컨테이너는 테스트 클래스마다 새로 띄우지 않고 한 번 띄워 같이 쓴다. 종료는 테스트 JVM이 끝날 때 Testcontainers가 처리한다.
 */
public abstract class MySqlContainerTest {

  // 잠금 대기 한도를 줄인다. 경합이 재현되지 않고 꼬이면 무한정 기다리는 대신 10초 뒤 실패하게 한다(기본값은 행 잠금 50초, 테이블 잠금 1년).
  protected static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0")
          .withCommand("--innodb-lock-wait-timeout=10", "--lock-wait-timeout=10");

  static {
    MYSQL.start();
  }

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
  }

  /**
   * 다른 스레드의 트랜잭션이 잠금을 기다리기 시작할 때까지 기다린다. 기다리는 쪽은 DB 서버라서 자바 스레드 상태로는 알 수 없다(응답을 기다리는 동안에도
   * RUNNABLE). 그래서 MySQL의 잠금 표에 기다리는 잠금이 있는지 직접 묻는다. 이 조회에는 권한이 필요해 root로 붙는다.
   *
   * <p>information_schema.innodb_trx를 쓰지 않는 이유: 그 표는 0.1초 안에 다시 읽으면 캐시를 갱신하지 않아서, 짧은 간격으로 계속 물으면
   * 대기가 시작돼도 끝내 보이지 않는다.
   */
  protected static void awaitLockWait(Thread waiter, AtomicReference<Throwable> failure)
      throws Exception {
    try (Connection root =
            DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
        PreparedStatement lockWaits =
            root.prepareStatement(
                "SELECT COUNT(*) FROM performance_schema.data_locks WHERE lock_status = 'WAITING'")) {
      long deadline = System.currentTimeMillis() + 5_000;
      while (true) {
        try (ResultSet result = lockWaits.executeQuery()) {
          result.next();
          if (result.getInt(1) > 0) {
            return;
          }
        }
        if (!waiter.isAlive() || System.currentTimeMillis() > deadline) {
          throw new AssertionError("잠금을 기다리지 않고 끝났습니다", failure.get());
        }
        Thread.sleep(10);
      }
    }
  }

  /** 다른 트랜잭션처럼 동작할 작업을 새 스레드에서 시작한다. 던진 예외는 failure에 담는다. */
  protected static Thread startInAnotherThread(Runnable task, AtomicReference<Throwable> failure) {
    Thread thread =
        new Thread(
            () -> {
              try {
                task.run();
              } catch (Throwable e) {
                failure.set(e);
              }
            });
    thread.start();
    return thread;
  }
}
