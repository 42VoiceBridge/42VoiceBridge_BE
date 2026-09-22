package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UploadDiagnosisRecordingService implements UploadDiagnosisRecordingUseCase {

  private final DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  private final RecordingRepositoryPort recordingRepositoryPort;
  private final StoragePort storagePort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public UploadResult upload(UploadCommand command) {
    DiagnosisSession session =
        diagnosisSessionRepositoryPort
            .findById(command.sessionId())
            .orElseThrow(
                () -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "진단 세션을 찾을 수 없습니다."));

    // Recording.userId는 세션과 중복 보관하는 값이라, 여기서 소유권을 검증해야 그 값을 신뢰할 수 있다.
    if (!session.isOwnedBy(command.userId())) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    if (!session.getSentenceIds().contains(command.sentenceId())) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "이 세션에 포함된 문장이 아닙니다.");
    }

    String s3Path = storagePort.upload(command.audioBytes(), command.fileName());

    Recording recording =
        Recording.create(command.sessionId(), command.sentenceId(), command.userId(), s3Path);
    recording.markProcessing();
    Recording saved = recordingRepositoryPort.save(recording);

    // 리스너는 AFTER_COMMIT에 걸려 있어 이 트랜잭션이 커밋된 뒤에 실행된다.
    eventPublisher.publishEvent(new RecordingUploadedEvent(saved.getId(), command.audioBytes()));

    return new UploadResult(saved.getId(), saved.getStatus().name());
  }
}
