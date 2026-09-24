package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetDiagnosisSessionUseCase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DiagnosisSessionResponse(
    UUID sessionId, String status, List<SentenceDto> sentences, LocalDateTime createdAt) {

  public static DiagnosisSessionResponse from(GetDiagnosisSessionUseCase.GetResult result) {
    List<SentenceDto> sentences =
        result.sentences().stream()
            .map(
                s ->
                    new SentenceDto(s.sentenceId(), s.text(), s.recordingId(), s.recordingStatus()))
            .toList();
    return new DiagnosisSessionResponse(
        result.sessionId(), result.status(), sentences, result.createdAt());
  }

  public record SentenceDto(
      UUID sentenceId, String text, UUID recordingId, String recordingStatus) {}
}
