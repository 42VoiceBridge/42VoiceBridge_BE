package com.voicebridge.port.in;

import java.util.UUID;

/** API 명세서 2.3절. 접수만 하고 AI 인식은 커밋 이후 비동기로 진행된다(RecordingRecognitionHandler). */
public interface UploadDiagnosisRecordingUseCase {

  UploadResult upload(UploadCommand command);

  record UploadCommand(
      UUID userId, UUID sessionId, UUID sentenceId, byte[] audioBytes, String fileName) {}

  record UploadResult(UUID recordingId, UUID sentenceId, String status) {}
}
