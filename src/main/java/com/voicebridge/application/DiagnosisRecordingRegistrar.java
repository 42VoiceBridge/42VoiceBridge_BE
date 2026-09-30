package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase.UploadCommand;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.RecordingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진단 녹음 업로드의 DB 쪽 두 단계. 업로드 서비스는 이 둘 사이에서 S3 업로드를 트랜잭션 없이 한다. 세션을 잠근 채 S3를 기다리면 그동안 같은 세션의 다른 업로드와
 * 분석 완료 판단이 모두 멈추기 때문이다.
 *
 * <p>업로드 서비스와 별도의 빈이어야 한다. 같은 클래스 안에서 호출하면 프록시를 거치지 않아 @Transactional이 무시된다.
 */
@Component
@RequiredArgsConstructor
public class DiagnosisRecordingRegistrar {

  private final DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;
  private final RecordingRepositoryPort recordingRepositoryPort;
  private final ApplicationEventPublisher eventPublisher;

  /** 파일을 올리기 전에 거절할 요청을 거른다. 특히 남의 세션이면 그 파일이 저장소에 남으면 안 된다. */
  @Transactional(readOnly = true)
  public void verifyUploadable(UploadCommand command) {
    DiagnosisSession session =
        diagnosisSessionRepositoryPort
            .findById(command.sessionId())
            .orElseThrow(DiagnosisRecordingRegistrar::sessionNotFound);

    // Recording.userId는 세션과 중복 보관하는 값이라, 여기서 소유권을 검증해야 그 값을 신뢰할 수 있다.
    if (!session.isOwnedBy(command.userId())) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    session.ensureRecordable();
    if (!session.getSentenceIds().contains(command.sentenceId())) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "이 세션에 포함된 문장이 아닙니다.");
    }
  }

  /**
   * 세션을 잠근 뒤 아직 녹음을 받을 수 있는지 다시 확인하고 등록한다. S3에 올리는 사이 마지막 녹음의 인식이 끝나 세션이 ANALYZED가 됐을 수 있다. 분석 완료
   * 판단(DiagnosisSessionAnalysisTrigger)도 같은 잠금을 잡으므로, 둘 중 늦은 쪽은 먼저 끝난 쪽의 결과를 보고 판단한다.
   *
   * <p>여기서 거절되면 이미 올린 파일은 저장소에 남는다. 드물고 어떤 녹음도 가리키지 않는 파일이라 그대로 둔다.
   */
  @Transactional
  public Recording register(UploadCommand command, String s3Path) {
    DiagnosisSession session =
        diagnosisSessionRepositoryPort
            .findByIdForUpdate(command.sessionId())
            .orElseThrow(DiagnosisRecordingRegistrar::sessionNotFound);
    session.ensureRecordable();

    Recording recording =
        Recording.create(command.sessionId(), command.sentenceId(), command.userId(), s3Path);
    recording.markProcessing();
    Recording saved = recordingRepositoryPort.save(recording);

    // 리스너는 AFTER_COMMIT에 걸려 있어 이 트랜잭션이 커밋된 뒤에 실행된다.
    eventPublisher.publishEvent(new RecordingUploadedEvent(saved.getId(), command.audioBytes()));
    return saved;
  }

  private static CustomException sessionNotFound() {
    return new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "진단 세션을 찾을 수 없습니다.");
  }
}
