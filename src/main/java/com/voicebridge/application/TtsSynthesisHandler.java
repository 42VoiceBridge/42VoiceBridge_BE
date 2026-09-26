package com.voicebridge.application;

import com.voicebridge.domain.recognition.TtsRequest;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TtsEnginePort;
import com.voicebridge.port.out.TtsRequestRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 요청 서비스와 별도의 빈이어야 한다. 같은 클래스 안에서 호출하면 프록시를 거치지 않아 @Async가 무시된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsSynthesisHandler {

  private static final String AUDIO_FILE_NAME = "tts.mp3";

  private final TtsRequestRepositoryPort ttsRequestRepositoryPort;
  private final TtsEnginePort ttsEnginePort;
  private final StoragePort storagePort;

  /**
   * AFTER_COMMIT이라 요청 트랜잭션이 커밋된 뒤에만 실행된다. 이게 없으면 비동기 스레드가 아직 커밋되지 않은 TtsRequest를 조회해 찾지 못한다.
   *
   * <p>여기서 실패를 HTTP 응답으로 알릴 수 없으므로(이미 202를 돌려준 뒤다) 예외를 FAILED 상태로 남겨 상태 조회 API가 전달하게 한다.
   */
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(TtsRequestedEvent event) {
    TtsRequest ttsRequest = ttsRequestRepositoryPort.findById(event.ttsId()).orElse(null);
    if (ttsRequest == null) {
      log.warn("[TTS 합성] 요청을 찾을 수 없어 건너뜀 ttsId={}", event.ttsId());
      return;
    }

    try {
      byte[] audio = ttsEnginePort.synthesize(event.confirmedText());
      String audioUrl = storagePort.upload(audio, AUDIO_FILE_NAME);
      ttsRequest.markCompleted(audioUrl);
    } catch (Exception e) {
      log.error("[TTS 합성] 실패 ttsId={}", event.ttsId(), e);
      ttsRequest.markFailed();
    }

    ttsRequestRepositoryPort.save(ttsRequest);
  }
}
