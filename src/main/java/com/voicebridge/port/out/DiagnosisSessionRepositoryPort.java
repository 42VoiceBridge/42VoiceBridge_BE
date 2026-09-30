package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.DiagnosisSessionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosisSessionRepositoryPort {

  DiagnosisSession save(DiagnosisSession session);

  Optional<DiagnosisSession> findById(UUID id);

  /**
   * 세션을 잠그고 읽는다. 같은 세션을 잠근 다른 트랜잭션이 끝날 때까지 기다렸다가 그 결과를 본다. 녹음 추가와 분석 완료 판단이 서로의 중간 상태를 보지 않게 줄 세우는
   * 데 쓴다. 잠금은 트랜잭션이 끝날 때 풀리므로 트랜잭션 안에서만 의미가 있다.
   */
  Optional<DiagnosisSession> findByIdForUpdate(UUID id);

  List<DiagnosisSession> findByUserIdAndStatusIn(
      UUID userId, Collection<DiagnosisSessionStatus> statuses);
}
