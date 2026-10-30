package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 개인화·진단·문장 추천 기능 플래그. 테스트 설정은 기존 테스트를 위해 개인화·진단 플래그를 켜 두므로, 여기서 끈 상태를 따로 확인한다.
 *
 * <p>로그인한 사용자 기준이다. 토큰이 없으면 주소가 있든 없든 보안 필터가 먼저 401을 돌려준다.
 */
class FeatureFlagTest {

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {
        "voicebridge.features.personalization=false",
        "voicebridge.features.diagnosis=false"
      })
  class 플래그를_끄면 {

    @Autowired MockMvc mvc;

    private final UUID userId = UUID.randomUUID();

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
      return request.with(
          authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())));
    }

    @Test
    void 개인화_API는_404다() throws Exception {
      UUID id = UUID.randomUUID();
      for (MockHttpServletRequestBuilder request :
          List.of(
              post("/api/v1/personalization/recordings"),
              delete("/api/v1/personalization/recordings/" + id),
              post("/api/v1/personalization/train"),
              get("/api/v1/personalization/train/" + id),
              get("/api/v1/personalization/model"))) {
        mvc.perform(asUser(request))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
      }
    }

    @Test
    void 진단_자모통계_추천_API는_404다() throws Exception {
      UUID id = UUID.randomUUID();
      for (MockHttpServletRequestBuilder request :
          List.of(
              post("/api/v1/diagnosis-sessions"),
              get("/api/v1/diagnosis-sessions/" + id),
              post("/api/v1/diagnosis-sessions/" + id + "/recordings"),
              get("/api/v1/diagnosis-sessions/" + id + "/recordings/" + id + "/result"),
              get("/api/v1/users/me/jamo-error-stats"),
              post("/api/v1/users/me/recommendations"))) {
        mvc.perform(asUser(request))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
      }
    }

    @Test
    void 문장_추천_API는_404다() throws Exception {
      // 문구 삭제 주소(/phrases/{id})에 걸려 405가 되지 않아야 한다
      mvc.perform(
              asUser(post("/api/v1/phrases/suggestions"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"text\": \"물 주세요\"}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 남겨_둔_API는_그대로_동작한다() throws Exception {
      mvc.perform(asUser(get("/api/v1/recognitions"))).andExpect(status().isOk());
      mvc.perform(asUser(get("/api/v1/phrases"))).andExpect(status().isOk());
    }
  }

  @Test
  void 설정이_없으면_꺼진_것으로_본다() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                PersonalizationController.class,
                DiagnosisSessionController.class,
                JamoErrorStatsController.class,
                RecommendationController.class,
                PhraseSuggestionController.class);

    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(PersonalizationController.class)
                .doesNotHaveBean(DiagnosisSessionController.class)
                .doesNotHaveBean(JamoErrorStatsController.class)
                .doesNotHaveBean(RecommendationController.class)
                .doesNotHaveBean(PhraseSuggestionController.class));
  }

  @Test
  void 운영_기본값은_꺼져_있다() throws Exception {
    var main =
        new YamlPropertySourceLoader()
            .load("main", new FileSystemResource("src/main/resources/application.yml"))
            .get(0);

    // 환경변수가 없을 때의 값(${...:false})이 false여야 한다
    assertThat(main.getProperty("voicebridge.features.personalization"))
        .hasToString("${FEATURE_PERSONALIZATION_ENABLED:false}");
    assertThat(main.getProperty("voicebridge.features.diagnosis"))
        .hasToString("${FEATURE_DIAGNOSIS_ENABLED:false}");
    assertThat(main.getProperty("voicebridge.features.suggestion"))
        .hasToString("${FEATURE_SUGGESTION_ENABLED:false}");
  }
}
