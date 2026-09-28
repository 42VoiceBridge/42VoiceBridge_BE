package com.voicebridge.application;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 인식 핸들러와 별도의 빈이어야 한다. 같은 클래스 안에서 호출하면 프록시를 거치지 않아 @Transactional과 이벤트 리스너가 무시된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisSessionAnalysisTrigger {

  private final DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  private final RecordingRepositoryPort recordingRepositoryPort;

  /**
   * 녹음 인식 결과가 커밋된 뒤, 새 트랜잭션에서 세션이 끝났는지 판단한다.
   *
   * <p>인식 트랜잭션 안에서 판단하면 안 된다. 마지막 두 녹음의 인식이 거의 동시에 끝나면 각자 상대의 커밋 전 상태(PROCESSING)를 보고 둘 다 "아직"이라고
   * 판단해, 녹음이 전부 DONE인데도 세션이 영원히 IN_PROGRESS에 남는다. 커밋 뒤에 판단하면 마지막으로 커밋된 녹음의 판단은 앞선 녹음이 모두 커밋된 상태를
   * 본다. 반대로 둘 다 "완료"로 판단하는 경우는 같은 값(ANALYZED)을 두 번 저장할 뿐이라 해가 없다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRecordingRecognized(RecordingRecognizedEvent event) {
    DiagnosisSession session =
        diagnosisSessionRepositoryPort.findById(event.sessionId()).orElse(null);
    if (session == null) {
      log.warn("[세션 분석] 세션을 찾을 수 없어 건너뜀 sessionId={}", event.sessionId());
      return;
    }

    List<Recording> recordings = recordingRepositoryPort.findBySessionId(session.getId());
    if (session.markAnalyzedIfAllSentencesDone(recordings)) {
      diagnosisSessionRepositoryPort.save(session);
      log.info("[세션 분석] 모든 문장 인식 완료 → ANALYZED sessionId={}", session.getId());
    }
  }
}
