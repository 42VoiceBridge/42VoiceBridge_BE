package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.domain.recognition.Recognition;
import com.voicebridge.port.in.RecognizeSpeechUseCase;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/*
 실 사용 인식 파트
 AI 모델 호출 시 userId로 모델 타입 결정하고
 AIInferenceClient.recognize 호출,
 호출 실패시 - 503 error
*/

// 서비스 빈으로 스프링에 해당 클래스 등록 및 롬복으로 생성자 코드 생성
@Service
@RequiredArgsConstructor
public class RecognizeSpeechService implements RecognizeSpeechUseCase {

  private final PersonalizationJobRepositoryPort personalizationJobRepositoryPort;
  private final AiInferenceClient aiInferenceClient;
  private final RecognitionRepositoryPort recognitionRepositoryPort;

  @Override
  public RecognizeResult recognize(UUID userId, byte[] audioBytes, String fileName) {
    if (userId == null || audioBytes == null || audioBytes.length == 0) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    /*
      모델 타입 결정시 personalizationJobRepositoryPort의 조회 기능 사용해서
      최신 완료 학습 작업이 있으면 확인하고 그 모델 타입 쓰고 없으면 기본 모델로 타입 설정
    */
    ModelType modelType =
        personalizationJobRepositoryPort.findLatestCompletedByUserId(userId).isPresent()
            ? ModelType.PERSONALIZED
            : ModelType.BASE_ADAPTED;

    AiInferenceClient.RecognitionResult result;
    /*
     음성, 모델 타입, 유저아이디를 넣어서 인식결과 생성 시도하고, 생성 실패하면 503 에러 전파
    */
    try {
      result = aiInferenceClient.recognize(audioBytes, modelType, userId);
    } catch (RuntimeException e) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }
    /*
       AI 응답 누락 또는 신뢰도 유효하지 않으면 저장 안하고 오류처리로 간다.
       하지만 인식한 글자가 없는 빈 문자열은 정상 결과로 허용
    */

    if (result == null
        || result.recognizedText() == null
        || !Double.isFinite(result.confidence())
        || result.confidence() < 0
        || result.confidence() > 1) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }
    // 도메인 객체 생성해서 save 인자로 넘긴다.
    // save()는 포트 구현체인 어댑터를 통해서 DB에 저장된다.
    // 결과 saved에는 저장 후 어댑터가 반환한 Recognition 객체 저장된다.
    Recognition saved =
        recognitionRepositoryPort.save(
            Recognition.create(userId, result.recognizedText(), modelType, result.confidence()));
    // 결과에서 필요한 값만 record로 꺼내서 리턴한다.
    return new RecognizeResult(
        saved.getId(),
        saved.getRecognizedText(),
        saved.getModelUsed().name(),
        saved.getConfidence());
  }
}
