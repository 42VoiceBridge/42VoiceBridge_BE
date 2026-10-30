package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.persistence.PhraseJpaRepository;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.domain.suggestion.SuggestionPolicy;
import com.voicebridge.port.out.CallRateLimitPort;
import com.voicebridge.port.out.LlmSuggestionPort;
import com.voicebridge.port.out.TtsEnginePort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서비스는 실제 빈, LLM과 호출 상한(Redis)만 가짜로 둔다. 검증 순서(400 → 429 → 503)를 HTTP 수준에서 확인하려는 것이다.
// 가짜 LLM은 실제 Claude 응답을 보장하지 않는다. 어댑터의 요청·응답 형식은 ClaudeSuggestionAdapterTest(WireMock)가, 실제 키 왕복은
// 수동으로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "voicebridge.features.suggestion=true")
class PhraseSuggestionIntegrationTest {

  private static final String URL = "/api/v1/phrases/suggestions";

  @Autowired MockMvc mvc;
  @Autowired PhraseJpaRepository phraseJpaRepository;
  @MockitoBean LlmSuggestionPort llm;
  @MockitoBean CallRateLimitPort rateLimit;
  @MockitoBean TtsEnginePort ttsEngine;

  private final UUID me = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    phraseJpaRepository.deleteAllInBatch();
    doReturn(true).when(rateLimit).tryAcquire(any(), any(), anyInt(), any());
  }

  private MockHttpServletRequestBuilder suggest(String body) {
    return post(URL)
        .with(authentication(new UsernamePasswordAuthenticationToken(me, null, List.of())))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  @Test
  void 후보를_걸러서_돌려주고_캐시하지_않으며_저장하지도_말하지도_않는다() throws Exception {
    doReturn(List.of("  물 좀 주세요.  ", "물 좀 주세요.", "", "줄\n바꿈", "x".repeat(101), "컵도 주세요."))
        .when(llm)
        .expand("물", PhraseCategory.RESTAURANT);

    mvc.perform(suggest("{\"text\": \"  물  \", \"category\": \"RESTAURANT\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.candidates.length()").value(2))
        .andExpect(jsonPath("$.data.candidates[0]").value("물 좀 주세요."))
        .andExpect(jsonPath("$.data.candidates[1]").value("컵도 주세요."));

    verify(rateLimit)
        .tryAcquire(
            eq(me),
            eq("suggestion"),
            eq(SuggestionPolicy.MAX_CALLS_PER_MINUTE),
            eq(Duration.ofMinutes(1)));
    assertThat(phraseJpaRepository.count()).isZero();
    verifyNoInteractions(ttsEngine);
  }

  @Test
  void 카테고리는_없어도_된다() throws Exception {
    doReturn(List.of("화장실이 어디예요?")).when(llm).expand("화장실", null);

    mvc.perform(suggest("{\"text\": \"화장실\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.candidates[0]").value("화장실이 어디예요?"));
  }

  @Test
  void 잘못된_입력은_400이고_LLM을_부르지_않고_호출_횟수도_쓰지_않는다() throws Exception {
    for (String body :
        List.of(
            "{\"text\": \"   \"}",
            "{}",
            "{\"text\": \"" + "가".repeat(101) + "\"}",
            "{\"text\": \"줄\\n바꿈\"}",
            "{\"text\": \"물\", \"category\": \"FOOD\"}",
            "{\"text\": \"물\", \"category\": \"restaurant\"}")) {
      mvc.perform(suggest(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
    verifyNoInteractions(llm, rateLimit);
  }

  @Test
  void 호출_상한을_넘으면_429이고_LLM을_부르지_않는다() throws Exception {
    doReturn(false).when(rateLimit).tryAcquire(any(), any(), anyInt(), any());

    mvc.perform(suggest("{\"text\": \"물\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    verifyNoInteractions(llm);
  }

  @Test
  void LLM이_실패하면_503이다() throws Exception {
    doThrow(new CustomException(ErrorCode.SUGGESTION_UNAVAILABLE)).when(llm).expand(any(), any());

    mvc.perform(suggest("{\"text\": \"물\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("SUGGESTION_UNAVAILABLE"));
  }

  @Test
  void 쓸_수_있는_후보가_하나도_없으면_503이다() throws Exception {
    doReturn(List.of("", "줄\n바꿈")).when(llm).expand(any(), any());

    mvc.perform(suggest("{\"text\": \"물\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("SUGGESTION_UNAVAILABLE"));
  }

  @Test
  void 로그인하지_않으면_401이고_LLM을_부르지_않는다() throws Exception {
    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"물\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(llm, rateLimit);
  }
}
