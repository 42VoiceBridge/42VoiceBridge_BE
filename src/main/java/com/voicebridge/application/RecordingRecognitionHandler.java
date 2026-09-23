package com.voicebridge.application;

import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.RecordingRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private final RecordingRepositoryPort recordingRepositoryPort;
  private final AiInferenceClient aiInferenceClient;

  /**
   * AFTER_COMMIT이라 업로드 트랜잭션이 커밋된 뒤에만 실행된다. 이게 없으면 비동기 스레드가 아직 커밋되지 않은 녹음을 조회해 찾지 못한다.
   *
   * <p>여기서 실패를 HTTP 응답으로 알릴 수 없으므로(이미 202를 돌려준 뒤다) 예외를 FAILED 상태로 남겨 결과 조회 API가 전달하게 한다.
   */
  @Async
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
          aiInferenceClient.recognize(
              event.audioBytes(), ModelType.BASE_ADAPTED, recording.getUserId());
      recording.markProcessed(result.recognizedText(), result.confidence());
    } catch (Exception e) {
      log.error("[인식] 실패 recordingId={}", event.recordingId(), e);
      recording.markFailed();
    }

    recordingRepositoryPort.save(recording);
  }
}
