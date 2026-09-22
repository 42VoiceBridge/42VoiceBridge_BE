package com.voicebridge.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.voicebridge.common.exception.*;
import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.domain.recognition.*;
import com.voicebridge.port.out.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecognizeSpeechServiceTest {
  @Mock PersonalizationJobRepositoryPort jobs;
  @Mock AiInferenceClient ai;
  @Mock RecognitionRepositoryPort recordings;
  RecognizeSpeechService service;
  final UUID userId = UUID.randomUUID();
  final byte[] audio = {1, 2, 3};

  @BeforeEach
  void setUp() {
    service = new RecognizeSpeechService(jobs, ai, recordings);
  }

  /*
   * 모델 타입에 따라서 단위 테스트 들어가기
   * */
  @ParameterizedTest
  @EnumSource(ModelType.class)
  void 모델_유무에_따라_선택하고_결과를_저장한다(ModelType model) {
    var job = PersonalizationJob.create(userId, 5);
    job.markInProgress();
    job.complete("v1", "models/v1.pt");
    when(jobs.findLatestCompletedByUserId(userId))
        .thenReturn(model == ModelType.PERSONALIZED ? Optional.of(job) : Optional.empty());
    when(ai.recognize(audio, model, userId))
        .thenReturn(new AiInferenceClient.RecognitionResult("안녕하세요", 0.8));
    when(recordings.save(any()))
        .thenAnswer(
            invocation -> {
              Recognition r = invocation.getArgument(0);
              assertThat(r.getUserId()).isEqualTo(userId);
              assertThat(r.getModelUsed()).isEqualTo(model);
              assertThat(r.getRecognizedText()).isEqualTo("안녕하세요");
              assertThat(r.getConfidence()).isEqualTo(0.8);
              assertThat(r.getCreatedAt()).isNotNull();
              return r;
            });
    var result = service.recognize(userId, audio, "voice.wav");
    assertThat(result.recognitionId()).isNotNull();
    assertThat(result.recognizedText()).isEqualTo("안녕하세요");
    assertThat(result.modelUsed()).isEqualTo(model.name());
    verify(recordings).save(argThat(r -> r.getId().equals(result.recognitionId())));
  }

  @ParameterizedTest
  @EnumSource(ModelType.class)
  void AI_실패는_503이며_재시도하거나_저장하지_않는다(ModelType model) {
    when(jobs.findLatestCompletedByUserId(userId))
        .thenReturn(
            model == ModelType.PERSONALIZED
                ? Optional.of(PersonalizationJob.create(userId, 5))
                : Optional.empty());
    when(ai.recognize(audio, model, userId)).thenThrow(new IllegalStateException("AI 오류"));
    assertThatThrownBy(() -> service.recognize(userId, audio, "voice.wav"))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_INFERENCE_UNAVAILABLE);
              assertThat(e.getErrorCode().getStatus().value()).isEqualTo(503);
            });
    verify(ai).recognize(audio, model, userId);
    verifyNoMoreInteractions(ai);
    verifyNoInteractions(recordings);
  }

  @Test
  void 무음의_빈_텍스트도_정상_저장한다() {
    when(jobs.findLatestCompletedByUserId(userId)).thenReturn(Optional.empty());
    when(ai.recognize(audio, ModelType.BASE_ADAPTED, userId))
        .thenReturn(new AiInferenceClient.RecognitionResult("", 0));
    when(recordings.save(any())).thenAnswer(i -> i.getArgument(0));
    assertThat(service.recognize(userId, audio, "silence.wav").recognizedText()).isEmpty();
  }

  @Test
  void 잘못된_AI_응답은_저장하지_않는다() {
    when(jobs.findLatestCompletedByUserId(userId)).thenReturn(Optional.empty());
    var invalid =
        new AiInferenceClient.RecognitionResult[] {
          null,
          new AiInferenceClient.RecognitionResult(null, 0),
          new AiInferenceClient.RecognitionResult("text", Double.NaN),
          new AiInferenceClient.RecognitionResult("text", Double.POSITIVE_INFINITY),
          new AiInferenceClient.RecognitionResult("text", -0.1),
          new AiInferenceClient.RecognitionResult("text", 1.1)
        };
    for (var response : invalid) {
      when(ai.recognize(audio, ModelType.BASE_ADAPTED, userId)).thenReturn(response);
      assertThatThrownBy(() -> service.recognize(userId, audio, "voice.wav"))
          .isInstanceOf(CustomException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }
    verifyNoInteractions(recordings);
  }

  @Test
  void 사용자나_음성이_없으면_외부_호출_전에_거부한다() {
    assertThatThrownBy(() -> service.recognize(null, audio, "voice.wav"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThatThrownBy(() -> service.recognize(userId, null, "voice.wav"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThatThrownBy(() -> service.recognize(userId, new byte[0], "voice.wav"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    verifyNoInteractions(jobs, ai, recordings);
  }

  @Test
  void DB_저장_오류를_AI_오류로_바꾸지_않는다() {
    when(jobs.findLatestCompletedByUserId(userId)).thenReturn(Optional.empty());
    when(ai.recognize(audio, ModelType.BASE_ADAPTED, userId))
        .thenReturn(new AiInferenceClient.RecognitionResult("text", 0.8));
    when(recordings.save(any())).thenThrow(new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> service.recognize(userId, audio, "voice.wav"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
  }
}
