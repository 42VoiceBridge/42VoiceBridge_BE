package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.in.GetDiagnosisSessionUseCase;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDiagnosisSessionService implements GetDiagnosisSessionUseCase {

  private final DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  private final SentenceRepositoryPort sentenceRepositoryPort;
  private final RecordingRepositoryPort recordingRepositoryPort;

  @Override
  public GetResult getSession(UUID userId, UUID sessionId) {
    DiagnosisSession session =
        diagnosisSessionRepositoryPort
            .findById(sessionId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "진단 세션을 찾을 수 없습니다."));

    if (!session.isOwnedBy(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }

    Map<UUID, String> textBySentenceId =
        sentenceRepositoryPort.findAllByIds(session.getSentenceIds()).stream()
            .collect(Collectors.toMap(Sentence::id, Sentence::text));

    // 세션 완료 판단과 같은 규칙으로 고른다. 그래야 화면에 모든 문장이 DONE으로 보일 때 세션도 ANALYZED가 된다.
    Map<UUID, Recording> latestBySentenceId =
        Recording.latestPerSentence(recordingRepositoryPort.findBySessionId(sessionId)).stream()
            .collect(Collectors.toMap(Recording::getSentenceId, Function.identity()));

    // 세션의 문장 순서가 곧 낭독 순서다(OrderColumn으로 보존됨). 그 순서를 그대로 유지한다.
    List<SentenceView> sentences =
        session.getSentenceIds().stream()
            .map(
                sentenceId -> {
                  Recording recording = latestBySentenceId.get(sentenceId);
                  return new SentenceView(
                      sentenceId,
                      textBySentenceId.get(sentenceId),
                      recording == null ? null : recording.getId(),
                      recording == null ? null : recording.getStatus().name());
                })
            .toList();

    return new GetResult(
        session.getId(), session.getStatus().name(), sentences, session.getCreatedAt());
  }
}
