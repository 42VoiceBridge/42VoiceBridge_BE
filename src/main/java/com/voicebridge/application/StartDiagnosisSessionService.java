package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.in.StartDiagnosisSessionUseCase;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class StartDiagnosisSessionService implements StartDiagnosisSessionUseCase {

  private static final int DIAGNOSIS_SENTENCE_COUNT = 5;

  private final SentenceRepositoryPort sentenceRepositoryPort;
  private final DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;

  @Override
  public StartResult start(UUID userId) {
    List<Sentence> sentences =
        sentenceRepositoryPort.findDiagnosisSentences(DIAGNOSIS_SENTENCE_COUNT);

    if (sentences.isEmpty()) {
      // 문장 마스터 데이터가 아직 시딩되지 않은 상태 — 실제 서비스 불가 상태를 명확히 알린다.
      throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "낭독할 문장이 아직 준비되지 않았습니다.");
    }

    List<UUID> sentenceIds = sentences.stream().map(Sentence::id).toList();
    DiagnosisSession session = DiagnosisSession.start(userId, sentenceIds);
    DiagnosisSession saved = diagnosisSessionRepositoryPort.save(session);

    List<SentenceView> sentenceViews =
        sentences.stream().map(s -> new SentenceView(s.id(), s.text())).toList();

    return new StartResult(
        saved.getId(), saved.getStatus().name(), sentenceViews, saved.getCreatedAt());
  }
}
