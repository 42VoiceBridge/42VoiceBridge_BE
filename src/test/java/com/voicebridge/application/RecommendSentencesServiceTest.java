package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recommendation.ShownPrompt;
import com.voicebridge.port.in.RecommendSentencesUseCase.RecommendationResult;
import com.voicebridge.port.in.RecommendSentencesUseCase.RecommendedSentence;
import com.voicebridge.port.out.EnrollmentPromptPort;
import com.voicebridge.port.out.EnrollmentPromptPort.Prompt;
import com.voicebridge.port.out.EnrollmentPromptPort.PromptBatch;
import com.voicebridge.port.out.ShownPromptRepositoryPort;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendSentencesServiceTest {

  @Mock private ShownPromptRepositoryPort shownPromptRepositoryPort;
  @Mock private EnrollmentPromptPort enrollmentPromptPort;

  private RecommendSentencesService service;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new RecommendSentencesService(shownPromptRepositoryPort, enrollmentPromptPort);
  }

  /** AI는 요청받은 seed를 그대로 돌려준다(AI 계약 §3.6). */
  private void aiReturns(Prompt... prompts) {
    when(enrollmentPromptPort.nextPrompts(eq(userId), anyInt(), anyLong(), anyCollection()))
        .thenAnswer(
            invocation ->
                new PromptBatch(
                    "random", "prompt-random-v1", invocation.getArgument(2), List.of(prompts)));
  }

  private long capturedSeed() {
    ArgumentCaptor<Long> seed = ArgumentCaptor.forClass(Long.class);
    verify(enrollmentPromptPort).nextPrompts(eq(userId), anyInt(), seed.capture(), anyCollection());
    return seed.getValue();
  }

  @Test
  void 이미_보여준_문장은_빼달라고_요청한다() {
    when(shownPromptRepositoryPort.findPromptIdsByUserId(userId)).thenReturn(Set.of("02-03-0001"));
    aiReturns(new Prompt("02-04-0002", "서울역으로 가주세요."));

    service.recommend(userId, 5);

    verify(enrollmentPromptPort)
        .nextPrompts(eq(userId), eq(5), anyLong(), eq(Set.of("02-03-0001")));
  }

  @Test
  void 받은_문장을_AI에_보낸_seed와_함께_기록하고_돌려준다() {
    when(shownPromptRepositoryPort.findPromptIdsByUserId(userId)).thenReturn(Set.of());
    aiReturns(new Prompt("02-03-0001", "식당이 어디예요?"), new Prompt("06-01-0003", "물 좀 주세요."));

    RecommendationResult result = service.recommend(userId, 2);

    assertThat(result.sentences())
        .containsExactly(
            new RecommendedSentence("02-03-0001", "식당이 어디예요?"),
            new RecommendedSentence("06-01-0003", "물 좀 주세요."));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ShownPrompt>> saved = ArgumentCaptor.forClass(List.class);
    verify(shownPromptRepositoryPort).saveAll(saved.capture());
    long seed = capturedSeed();
    // 기록된 seed가 실제로 쓴 seed와 달라지면 나중에 같은 추천을 재현할 수 없다
    assertThat(saved.getValue())
        .allSatisfy(
            shown -> {
              assertThat(shown.getSeed()).isEqualTo(seed);
              assertThat(shown.getStrategyVersion()).isEqualTo("prompt-random-v1");
              assertThat(shown.getUserId()).isEqualTo(userId);
            });
  }

  @Test
  void 호출할_때마다_seed를_새로_뽑는다() {
    when(shownPromptRepositoryPort.findPromptIdsByUserId(userId)).thenReturn(Set.of());
    aiReturns(new Prompt("02-03-0001", "식당이 어디예요?"));

    service.recommend(userId, 1);
    service.recommend(userId, 1);

    ArgumentCaptor<Long> seeds = ArgumentCaptor.forClass(Long.class);
    verify(enrollmentPromptPort, times(2))
        .nextPrompts(eq(userId), anyInt(), seeds.capture(), anyCollection());
    assertThat(seeds.getAllValues().get(0)).isNotEqualTo(seeds.getAllValues().get(1));
  }

  @Test
  void 개수를_정하지_않으면_기본_10개를_요청한다() {
    when(shownPromptRepositoryPort.findPromptIdsByUserId(userId)).thenReturn(Set.of());
    aiReturns();

    service.recommend(userId, null);

    verify(enrollmentPromptPort).nextPrompts(eq(userId), eq(10), anyLong(), anyCollection());
  }

  @Test
  void 범위를_벗어난_개수는_AI를_부르기_전에_거절한다() {
    assertThatThrownBy(() -> service.recommend(userId, 51))
        .isInstanceOf(IllegalArgumentException.class);

    verify(enrollmentPromptPort, never()).nextPrompts(any(), anyInt(), anyLong(), anyCollection());
  }

  @Test
  void 받은_문장이_없으면_기록하지_않고_빈_목록을_돌려준다() {
    when(shownPromptRepositoryPort.findPromptIdsByUserId(userId)).thenReturn(Set.of());
    aiReturns();

    assertThat(service.recommend(userId, 10).sentences()).isEmpty();
    verify(shownPromptRepositoryPort, never()).saveAll(any());
  }

  @Test
  void AI가_실패하면_기록하지_않는다() {
    when(shownPromptRepositoryPort.findPromptIdsByUserId(userId)).thenReturn(Set.of());
    when(enrollmentPromptPort.nextPrompts(eq(userId), anyInt(), anyLong(), anyCollection()))
        .thenThrow(new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE));

    assertThatThrownBy(() -> service.recommend(userId, 10))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_INFERENCE_UNAVAILABLE);
    verify(shownPromptRepositoryPort, never()).saveAll(any());
  }
}
