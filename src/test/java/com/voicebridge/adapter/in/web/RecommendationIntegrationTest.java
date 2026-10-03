package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.port.out.EnrollmentPromptPort;
import com.voicebridge.port.out.EnrollmentPromptPort.Prompt;
import com.voicebridge.port.out.EnrollmentPromptPort.PromptBatch;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 서비스와 DB는 실제 빈을 쓰고 AI만 가짜로 둔다. 개수 규칙이 도메인에 있어서 서비스를 가짜로 두면 400을 HTTP 수준에서 확인할 수 없고,
// "두 번째 추천이 첫 번째 문장을 뺀다"는 저장과 조회가 실제로 이어져야 성립한다. 테스트마다 새 사용자 UUID를 써서 서로 섞이지 않는다.
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationIntegrationTest {

  private static final String URL = "/api/v1/users/me/recommendations";

  @Autowired MockMvc mvc;
  @MockitoBean EnrollmentPromptPort enrollmentPromptPort;

  private RequestPostProcessor asUser(UUID id) {
    return authentication(new UsernamePasswordAuthenticationToken(id, null, List.of()));
  }

  private void aiReturns(UUID userId, Prompt... prompts) {
    when(enrollmentPromptPort.nextPrompts(eq(userId), anyInt(), anyLong(), anyCollection()))
        .thenAnswer(
            invocation ->
                new PromptBatch(
                    "random",
                    "prompt-random-v1",
                    invocation.getArgument(2),
                    "script-pool-v1",
                    "0123456789abcdef",
                    List.of(prompts)));
  }

  @Test
  void 추천_문장을_돌려주고_두_번째_요청에서는_앞서_보여준_문장을_뺀다() throws Exception {
    UUID userId = UUID.randomUUID();
    aiReturns(userId, new Prompt("02-03-0001", "식당이 어디예요?"));

    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": 1}")
                .with(asUser(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sentences[0].shownPromptId").isNotEmpty())
        .andExpect(jsonPath("$.data.sentences[0].promptId").value("02-03-0001"))
        .andExpect(jsonPath("$.data.sentences[0].text").value("식당이 어디예요?"));

    mvc.perform(post(URL).with(asUser(userId))).andExpect(status().isOk());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
    verify(enrollmentPromptPort, times(2))
        .nextPrompts(eq(userId), anyInt(), anyLong(), excluded.capture());
    assertThat(excluded.getAllValues().get(0)).isEmpty();
    assertThat(excluded.getAllValues().get(1)).containsExactly("02-03-0001");
  }

  @Test
  void 본문_없이_요청하면_기본_10개를_요청한다() throws Exception {
    UUID userId = UUID.randomUUID();
    aiReturns(userId);

    mvc.perform(post(URL).with(asUser(userId))).andExpect(status().isOk());

    verify(enrollmentPromptPort).nextPrompts(eq(userId), eq(10), anyLong(), anyCollection());
  }

  @Test
  void 범위를_벗어난_개수는_400으로_거절한다() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\": 51}")
                .with(asUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 로그인하지_않으면_추천받을_수_없다() throws Exception {
    mvc.perform(post(URL)).andExpect(status().isUnauthorized());
  }
}
