package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetRecordingResultUseCase;
import java.util.List;

public record RecordingResultResponse(
    String status,
    String recognizedText,
    String answerText,
    Double confidence,
    List<DiffHighlightDto> diffHighlights) {

  public static RecordingResultResponse from(GetRecordingResultUseCase.ResultView result) {
    List<DiffHighlightDto> diffHighlights =
        result.diffHighlights().stream()
            .map(h -> new DiffHighlightDto(h.position(), h.expected(), h.recognized()))
            .toList();
    return new RecordingResultResponse(
        result.status(),
        result.recognizedText(),
        result.answerText(),
        result.confidence(),
        diffHighlights);
  }

  public record DiffHighlightDto(int position, String expected, String recognized) {}
}
