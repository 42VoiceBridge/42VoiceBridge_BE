package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetRecognitionHistoryUseCase.RecognitionView;
import com.voicebridge.port.in.RecognizeSpeechUseCase.RecognizeResult;
import java.util.UUID;

public record RecognitionResponse(
    UUID recognitionId, String recognizedText, String modelUsed, Double confidence) {
  // 이 정적 변환 메서드에서 이 record의 값의 변환을 한 곳에 모으기 위해서
  public static RecognitionResponse from(RecognizeResult result) {
    return new RecognitionResponse(
        result.recognitionId(), result.recognizedText(), result.modelUsed(), result.confidence());
  }

  public static RecognitionResponse from(RecognitionView result) {
    return new RecognitionResponse(
        result.recognitionId(), result.recognizedText(), result.modelUsed(), result.confidence());
  }
}
