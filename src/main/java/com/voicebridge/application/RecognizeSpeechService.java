package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.domain.recognition.Recognition;
import com.voicebridge.port.in.RecognizeSpeechUseCase;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.PersonalizationAdapterRepositoryPort;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/*
 실 사용 인식 파트
 AIInferenceClient.recognize 호출,
 호출 실패시 - 503 error
*/

@Service
@RequiredArgsConstructor
@Slf4j
public class RecognizeSpeechService implements RecognizeSpeechUseCase {

  private final PersonalizationAdapterRepositoryPort personalizationAdapterRepositoryPort;
  private final AiInferenceClient aiInferenceClient;
  private final RecognitionRepositoryPort recognitionRepositoryPort;
  // 스프링에서 생성한 객체를 주입받아서 사용하기 위함
  private final AudioNormalizationPort audioNormalizationPort;

  @Override
  public RecognizeResult recognize(UUID userId, byte[] audioBytes, String fileName) {
    if (userId == null || audioBytes == null || audioBytes.length == 0) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    // AI 호출 전 브라우저에서 온 업로드 음성을 정규화한다.
    var normalized = audioNormalizationPort.normalize(audioBytes);

    ModelType modelType =
        personalizationAdapterRepositoryPort.findActiveByUserId(userId).isPresent()
            ? ModelType.PERSONALIZED
            : ModelType.BASE_ADAPTED;

    AiInferenceClient.RecognitionResult result;

    try {
      // AI 입력 규격을 맞추기 위해 정규화된 레코드의 값을 전달한다.
      result = aiInferenceClient.recognize(normalized.wavBytes(), modelType, userId);
    } catch (RuntimeException e) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }
    /*
       AI 응답 누락 또는 신뢰도 유효하지 않으면 저장 안하고 오류처리로 간다.
       하지만 인식한 글자가 없는 빈 문자열은 정상 결과로 허용.
       confidence는 null이면(v1 계약상 score는 항상 null) 정상으로 허용하고,
       값이 있을 때만(v1 이후 대비) 범위를 검증한다.
    */

    if (result == null
        || result.recognizedText() == null
        || (result.confidence() != null
            && (!Double.isFinite(result.confidence())
                || result.confidence() < 0
                || result.confidence() > 1))) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }
    // 도메인 객체 생성해서 save 인자로 넘긴다.
    // save()는 포트 구현체인 어댑터를 통해서 DB에 저장된다.
    // 결과 saved에는 저장 후 어댑터가 반환한 Recognition 객체 저장된다.
    Recognition saved =
        recognitionRepositoryPort.save(
            Recognition.create(userId, result.recognizedText(), modelType, result.confidence()));
    // save()가 성공하면, 인식 ID와 변환 정보를 로그로 연결한다.
    // Id는 DB에 남지만 메타데이터는 버려진다. 그 메타 데이터를 로그에 남기기 위해서 로그에 값 남긴다.
    log.info(
        "Recognition audio provenance: recognitionId={}, metadata={}",
        saved.getId(),
        normalized.metadata());
    return new RecognizeResult(
        saved.getId(),
        saved.getRecognizedText(),
        saved.getModelUsed().name(),
        saved.getConfidence());
  }
}
