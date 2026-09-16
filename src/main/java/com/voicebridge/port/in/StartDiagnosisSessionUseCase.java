package com.voicebridge.port.in;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StartDiagnosisSessionUseCase {

  StartResult start(UUID userId);

  record StartResult(
      UUID sessionId, String status, List<SentenceView> sentences, LocalDateTime createdAt) {}

  record SentenceView(UUID sentenceId, String text) {}
}
