package com.voicebridge.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// @EnableAsync가 없으면 @Async가 아무 효과 없이 동기로 실행된다(에러도 나지 않음).
@Configuration
@EnableAsync
public class AsyncConfig {

  /** 진단 녹음 인식 전용 실행기. @Async의 값으로 이 이름을 쓴다. */
  public static final String DIAGNOSIS_RECOGNITION_EXECUTOR = "diagnosisRecognitionExecutor";

  /**
   * 진단 녹음 인식만 따로 돌린다. 스프링 부트 기본 실행기(스레드 8개, 대기열 무제한)를 TTS 합성과 같이 쓰면, 진단이 몰릴 때 스레드가 전부 AI 응답을 기다리느라
   * TTS가 대기열 뒤로 밀린다. 진단은 몇 초 늦어도 되지만 TTS는 사용자가 지금 말하려는 순간이다.
   *
   * <p>스레드를 적게 두는 이유: AI 서버는 요청을 한 번에 하나씩 처리하므로 스레드를 늘려도 AI 앞에서 줄만 서고, 뒤쪽 요청은 그 줄에서 기다리다 타임아웃에 걸린다.
   *
   * <p>대기열이 넘치면 요청을 버리지 않고 호출한 스레드(업로드 요청)가 직접 인식한다. 그 업로드 응답만 느려지고 녹음이 PROCESSING에 갇히지 않는다.
   *
   * <p>이 빈을 등록하면 스프링 부트가 기본 실행기를 만들지 않을 수 있어, 설정에 spring.task.execution.mode=force를 둔다. 없으면 TTS 같은
   * 다른 {@code @Async}까지 이 실행기로 몰린다.
   */
  @Bean(name = DIAGNOSIS_RECOGNITION_EXECUTOR)
  public ThreadPoolTaskExecutor diagnosisRecognitionExecutor(
      @Value("${voicebridge.diagnosis.recognition.threads}") int threads,
      @Value("${voicebridge.diagnosis.recognition.queue-capacity}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(threads);
    executor.setMaxPoolSize(threads);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("diagnosis-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
  }
}
