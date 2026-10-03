package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.Recording;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.InvalidAudioException;
import com.voicebridge.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// @Transactional을 걸지 않는다. 변환과 S3 업로드를 트랜잭션 밖에 두고, 앞뒤의 DB 작업만 DiagnosisRecordingRegistrar가 각자의
// 트랜잭션으로 처리한다.
@Service
@RequiredArgsConstructor
public class UploadDiagnosisRecordingService implements UploadDiagnosisRecordingUseCase {

  // 저장 키의 확장자를 정한다. 저장하는 것은 브라우저 원본이 아니라 변환된 WAV다.
  private static final String STORED_FILE_NAME = "recording.wav";

  private final DiagnosisRecordingRegistrar recordingRegistrar;
  private final AudioNormalizationPort audioNormalizationPort;
  private final StoragePort storagePort;

  @Override
  public UploadResult upload(UploadCommand command) {
    // 거절할 요청(남의 세션, 끝난 세션)에 변환기 자원을 쓰지 않도록 확인을 먼저 한다.
    recordingRegistrar.verifyUploadable(command);

    // AI는 WAV(PCM16·모노·16kHz)만 받는다. 업로드 순간에 변환해야 잘못된 녹음을 바로 알려줄 수 있다. 비동기 인식 단계에서 변환하면
    // 사용자는 다음 문장을 녹음하는 중에야 실패를 알게 된다. 변환기 용량·시간 초과(AudioProcessingException)는 전역 핸들러가 503으로
    // 바꾼다.
    byte[] wav = normalize(command.audioBytes());

    // 원본 대신 변환된 WAV를 저장한다. 저장된 파일이 곧 AI가 들은 파일이라 다시 인식하거나 원인을 추적하기 쉽다.
    String s3Path = storagePort.upload(wav, STORED_FILE_NAME);
    Recording saved = recordingRegistrar.register(command, s3Path, wav);
    return new UploadResult(saved.getId(), saved.getSentenceId(), saved.getStatus().name());
  }

  private byte[] normalize(byte[] audioBytes) {
    try {
      return audioNormalizationPort.normalize(audioBytes).wavBytes();
    } catch (InvalidAudioException e) {
      throw new CustomException(errorCodeOf(e.reason()));
    }
  }

  private static ErrorCode errorCodeOf(InvalidAudioException.Reason reason) {
    return switch (reason) {
      case TOO_SHORT -> ErrorCode.AUDIO_TOO_SHORT;
      case TOO_LONG -> ErrorCode.AUDIO_TOO_LONG;
      case INVALID -> ErrorCode.AUDIO_INVALID;
    };
  }
}
