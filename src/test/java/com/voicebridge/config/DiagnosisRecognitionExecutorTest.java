package com.voicebridge.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class DiagnosisRecognitionExecutorTest {

  private ThreadPoolTaskExecutor executor;
  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void setUp() {
    // 스레드 1개, 대기열 1칸으로 줄여 "넘치는" 상황을 쉽게 만든다
    executor = new AsyncConfig().diagnosisRecognitionExecutor(1, 1);
    executor.initialize();
  }

  @AfterEach
  void tearDown() {
    release.countDown();
    executor.shutdown();
  }

  private void blockUntilReleased() {
    try {
      release.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void 스레드와_대기열이_모두_차면_버리지_않고_호출한_스레드가_직접_처리한다() {
    executor.execute(this::blockUntilReleased); // 하나뿐인 스레드를 붙잡는다
    executor.execute(this::blockUntilReleased); // 대기열 한 칸을 채운다

    AtomicReference<String> ranOn = new AtomicReference<>();
    executor.execute(() -> ranOn.set(Thread.currentThread().getName()));

    // 버려졌다면 null, 다른 스레드로 갔다면 이름이 다르다
    assertThat(ranOn.get()).isEqualTo(Thread.currentThread().getName());
  }

  @Test
  void 스레드_수와_대기열은_설정값을_그대로_쓴다() {
    ThreadPoolTaskExecutor configured = new AsyncConfig().diagnosisRecognitionExecutor(2, 200);

    assertThat(configured.getCorePoolSize()).isEqualTo(2);
    assertThat(configured.getMaxPoolSize()).isEqualTo(2);
    assertThat(configured.getQueueCapacity()).isEqualTo(200);
    assertThat(configured.getThreadNamePrefix()).isEqualTo("diagnosis-");
  }
}
