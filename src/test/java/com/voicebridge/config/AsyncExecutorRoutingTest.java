package com.voicebridge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.application.RecordingRecognitionHandler;
import com.voicebridge.application.RecordingUploadedEvent;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;

/**
 * 진단 인식은 전용 실행기에서, 나머지 @Async(TTS 합성 등)는 스프링 부트 기본 실행기에서 도는지 실제 스레드 이름으로 확인한다. 전용 실행기를 빈으로 등록하면 부트가
 * 기본 실행기를 만들지 않을 수 있어서(spring.task.execution.mode), 설정이 빠지면 일반 @Async까지 진단 실행기로 몰린다.
 */
@SpringBootTest
@Import(AsyncExecutorRoutingTest.ThreadNameProbe.class)
class AsyncExecutorRoutingTest {

  @Autowired ThreadNameProbe probe;

  static class ThreadNameProbe {

    @Async
    public CompletableFuture<String> onDefaultExecutor() {
      return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    @Async(AsyncConfig.DIAGNOSIS_RECOGNITION_EXECUTOR)
    public CompletableFuture<String> onDiagnosisExecutor() {
      return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }
  }

  @Test
  void 실행기를_지정하지_않은_비동기_작업은_기본_실행기에서_돈다() throws Exception {
    assertThat(probe.onDefaultExecutor().get()).startsWith("task-");
  }

  @Test
  void 진단_실행기를_지정한_작업은_전용_실행기에서_돈다() throws Exception {
    assertThat(probe.onDiagnosisExecutor().get()).startsWith("diagnosis-");
  }

  @Test
  void 진단_인식_핸들러는_전용_실행기를_지정한다() throws Exception {
    Async async =
        RecordingRecognitionHandler.class
            .getMethod("handle", RecordingUploadedEvent.class)
            .getAnnotation(Async.class);

    assertThat(async.value()).isEqualTo(AsyncConfig.DIAGNOSIS_RECOGNITION_EXECUTOR);
  }
}
