package com.voicebridge.application;

import com.voicebridge.config.AsyncConfig;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.RecordingRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 업로드 서비스와 별도의 빈이어야 한다. 같은 클래스 안에서 호출하면 프록시를 거치지 않아 @Async가 무시된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingRecognitionHandler {

  /**
   * 진단은 개인화 여부와 상관없이 항상 기본 모델로 인식한다(2026-09-29 결정). 세션을 누적해도 서로 다른 모델의 결과가 한 통계에 섞이지 않고, 개인화 모델을
   * 유지하든 폐기하든 진단 쪽은 바뀌지 않는다. 진단은 "개인화하기 전 기본 모델이 어디서 틀리는가"를 보는 단계이기도 하다.
   *
   * <p>지금 HTTP 어댑터는 이 값을 AI에 전달하지 않아(use_adapter) 실제 고정은 어댑터가 전달하게 된 뒤부터다. BASE_ADAPTED는 BASE로 이름이
   * 바뀔 예정이다(공개 데이터로 1차 적응한 모델은 존재하지 않는다는 AI 쪽 요청).
   */
  private static final ModelType DIAGNOSIS_MODEL = ModelType.BASE_ADAPTED;

  private final RecordingRepositoryPort recordingRepositoryPort;
  private final AiInferenceClient aiInferenceClient;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * AFTER_COMMIT이라 업로드 트랜잭션이 커밋된 뒤에만 실행된다. 이게 없으면 비동기 스레드가 아직 커밋되지 않은 녹음을 조회해 찾지 못한다.
   *
   * <p>여기서 실패를 HTTP 응답으로 알릴 수 없으므로(이미 202를 돌려준 뒤다) 예외를 FAILED 상태로 남겨 결과 조회 API가 전달하게 한다.
   */
  @Async(AsyncConfig.DIAGNOSIS_RECOGNITION_EXECUTOR)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(RecordingUploadedEvent event) {
    Recording recording = recordingRepositoryPort.findById(event.recordingId()).orElse(null);
    if (recording == null) {
      log.warn("[인식] 녹음을 찾을 수 없어 건너뜀 recordingId={}", event.recordingId());
      return;
    }

    try {
      AiInferenceClient.RecognitionResult result =
          aiInferenceClient.recognize(event.audioBytes(), DIAGNOSIS_MODEL, recording.getUserId());
      recording.markProcessed(result.recognizedText(), result.confidence());
    } catch (Exception e) {
      log.error("[인식] 실패 recordingId={}", event.recordingId(), e);
      recording.markFailed();
    }

    recordingRepositoryPort.save(recording);

    // 세션이 끝났는지는 여기서 판단하지 않는다. 이 트랜잭션이 커밋된 뒤에 판단해야 동시에 끝난 다른 녹음의 결과를 볼 수 있다
    // (DiagnosisSessionAnalysisTrigger). FAILED는 세션을 끝낼 수 없으므로 알리지 않는다.
    if (recording.isDone()) {
      eventPublisher.publishEvent(new RecordingRecognizedEvent(recording.getSessionId()));
    }
  }
}
