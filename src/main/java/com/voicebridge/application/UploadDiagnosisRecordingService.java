package com.voicebridge.application;

import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase;
import com.voicebridge.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// @Transactional을 걸지 않는다. S3 업로드를 트랜잭션 밖에 두고, 앞뒤의 DB 작업만 DiagnosisRecordingRegistrar가 각자의 트랜잭션으로
// 처리한다.
@Service
@RequiredArgsConstructor
public class UploadDiagnosisRecordingService implements UploadDiagnosisRecordingUseCase {

  private final DiagnosisRecordingRegistrar recordingRegistrar;
  private final StoragePort storagePort;

  @Override
  public UploadResult upload(UploadCommand command) {
    recordingRegistrar.verifyUploadable(command);
    String s3Path = storagePort.upload(command.audioBytes(), command.fileName());
    Recording saved = recordingRegistrar.register(command, s3Path);
    return new UploadResult(saved.getId(), saved.getSentenceId(), saved.getStatus().name());
  }
}
