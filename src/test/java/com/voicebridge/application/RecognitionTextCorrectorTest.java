package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.CallRateLimitPort;
import com.voicebridge.port.out.LlmSuggestionPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecognitionTextCorrectorTest {

  @Mock LlmSuggestionPort llm;
  @Mock CallRateLimitPort rateLimit;

  private final UUID userId = UUID.randomUUID();

  private RecognitionTextCorrector corrector(boolean enabled) {
    return new RecognitionTextCorrector(llm, rateLimit, enabled);
  }

  private void allowCall() {
    when(rateLimit.tryAcquire(eq(userId), eq(RecognitionTextCorrector.FEATURE), anyInt(), any()))
        .thenReturn(true);
  }

  @Test
  void 꺼져_있으면_LLM을_부르지_않고_원문을_돌려준다() {
    assertThat(corrector(false).correct(userId, "물 좀 주세여")).isEqualTo("물 좀 주세여");

    verify(llm, never()).correct(any());
  }

  @Test
  void 켜져_있으면_보정본을_돌려준다() {
    allowCall();
    when(llm.correct("물 좀 주세여")).thenReturn("물 좀 주세요");

    assertThat(corrector(true).correct(userId, "물 좀 주세여")).isEqualTo("물 좀 주세요");
  }

  @Test
  void 빈_원문은_보정하지_않는다() {
    assertThat(corrector(true).correct(userId, "")).isEmpty();

    verify(llm, never()).correct(any());
  }

  @Test
  void LLM이_실패하면_원문을_돌려준다() {
    allowCall();
    when(llm.correct(any())).thenThrow(new CustomException(ErrorCode.SUGGESTION_UNAVAILABLE));

    assertThat(corrector(true).correct(userId, "물 좀 주세여")).isEqualTo("물 좀 주세여");
  }

  @Test
  void 보정본이_원문과_너무_다르면_원문을_돌려준다() {
    allowCall();
    when(llm.correct(any())).thenReturn("오늘 병원 예약을 취소하고 싶습니다");

    assertThat(corrector(true).correct(userId, "물 좀 주세여")).isEqualTo("물 좀 주세여");
  }

  @Test
  void 호출_상한이면_LLM을_부르지_않고_원문을_돌려준다() {
    when(rateLimit.tryAcquire(any(), any(), anyInt(), any())).thenReturn(false);

    assertThat(corrector(true).correct(userId, "물 좀 주세여")).isEqualTo("물 좀 주세여");
    verify(llm, never()).correct(any());
  }

  @Test
  void 호출_상한_저장소가_실패해도_원문을_돌려준다() {
    when(rateLimit.tryAcquire(any(), any(), anyInt(), any()))
        .thenThrow(new IllegalStateException("redis down"));

    assertThat(corrector(true).correct(userId, "물 좀 주세여")).isEqualTo("물 좀 주세여");
    verify(llm, never()).correct(any());
  }
}
