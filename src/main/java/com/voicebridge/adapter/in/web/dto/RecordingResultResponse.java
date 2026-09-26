package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetRecordingResultUseCase;

public record RecordingResultResponse(
    String status, String recognizedText, String answerText, Double confidence) {

  public static RecordingResultResponse from(GetRecordingResultUseCase.ResultView result) {
    return new RecordingResultResponse(
        result.status(), result.recognizedText(), result.answerText(), result.confidence());
  }
}
