package com.voicebridge.port.in;

import com.voicebridge.domain.diagnosis.RecognitionDiff;
import java.util.List;
import java.util.UUID;

/** API 명세서 2.4절. */
public interface GetRecordingResultUseCase {

  ResultView getResult(UUID userId, UUID sessionId, UUID recordingId);

  record ResultView(
      String status,
      String recognizedText,
      String answerText,
      Double confidence,
      List<RecognitionDiff.Highlight> diffHighlights) {}
}
