package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.in.GetRecordingResultUseCase;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRecordingResultService implements GetRecordingResultUseCase {

  private final RecordingRepositoryPort recordingRepositoryPort;
  private final SentenceRepositoryPort sentenceRepositoryPort;

  @Override
  public ResultView getResult(UUID userId, UUID sessionId, UUID recordingId) {
    Recording recording =
        recordingRepositoryPort
            .findById(recordingId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "녹음을 찾을 수 없습니다."));

    // Recording.userId는 세션과 중복 보관하는 값이라 세션을 다시 조회하지 않고 바로 검증한다(의도된 비정규화).
    if (!recording.isOwnedBy(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    // 경로의 sessionId와 실제 소속이 다르면 잘못된 요청이다.
    if (!recording.getSessionId().equals(sessionId)) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "해당 세션의 녹음이 아닙니다.");
    }

    String answerText =
        sentenceRepositoryPort.findAllByIds(List.of(recording.getSentenceId())).stream()
            .findFirst()
            .map(Sentence::text)
            .orElse(null);

    return new ResultView(
        recording.getStatus().name(),
        recording.getRecognizedText(),
        answerText,
        recording.getConfidence());
  }
}
