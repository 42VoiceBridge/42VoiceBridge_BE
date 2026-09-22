package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase;
import java.util.UUID;

public record UploadRecordingResponse(UUID recordingId, String status) {

  public static UploadRecordingResponse from(UploadDiagnosisRecordingUseCase.UploadResult result) {
    return new UploadRecordingResponse(result.recordingId(), result.status());
  }
}
