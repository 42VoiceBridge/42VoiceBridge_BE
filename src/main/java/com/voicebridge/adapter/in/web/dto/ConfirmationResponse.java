package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.ConfirmRecognitionUseCase.ConfirmResult;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConfirmationResponse(
    UUID confirmationId, String confirmedText, LocalDateTime confirmedAt) {
  public static ConfirmationResponse from(ConfirmResult result) {
    return new ConfirmationResponse(
        result.confirmationId(), result.confirmedText(), result.confirmedAt());
  }
}
