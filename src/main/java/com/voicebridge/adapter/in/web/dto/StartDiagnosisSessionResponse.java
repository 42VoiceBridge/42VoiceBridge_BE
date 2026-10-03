package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.StartDiagnosisSessionUseCase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StartDiagnosisSessionResponse(
    UUID sessionId, String status, List<SentenceDto> sentences, LocalDateTime createdAt) {
  public static StartDiagnosisSessionResponse from(
      StartDiagnosisSessionUseCase.StartResult result) {
    List<SentenceDto> sentences =
        result.sentences().stream().map(s -> new SentenceDto(s.sentenceId(), s.text())).toList();
    return new StartDiagnosisSessionResponse(
        result.sessionId(), result.status(), sentences, result.createdAt());
  }

  public record SentenceDto(UUID sentenceId, String text) {}
}
