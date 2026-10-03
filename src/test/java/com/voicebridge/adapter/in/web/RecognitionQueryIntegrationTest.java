package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.domain.recognition.Recognition;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecognitionQueryIntegrationTest {
  private static final String URL = "/api/v1/recognitions";
  @Autowired MockMvc mvc;
  @Autowired RecognitionRepositoryPort repository;
  @Autowired EntityManager entityManager;
  // 조회 기능은 AI를 호출하지 않으므로 네이티브 AI 초기화를 대체한다.
  @MockitoBean AiInferenceClient aiInferenceClient;

  @Test
  void 본인_결과만_최신순으로_페이지별_조회한다() throws Exception {
    UUID user = UUID.randomUUID();
    UUID old = seed(user, "이전 결과", 1);
    UUID middle = seed(user, "중간 결과", 2);
    UUID latest = seed(user, "최신 결과", 3);
    seed(UUID.randomUUID(), "다른 사용자", 4);
    entityManager.clear();
    mvc.perform(get(URL).param("page", "0").param("size", "2").with(asUser(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].recognitionId").value(latest.toString()))
        .andExpect(jsonPath("$.data.content[1].recognitionId").value(middle.toString()))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2));
    mvc.perform(get(URL).param("page", "1").param("size", "2").with(asUser(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].recognitionId").value(old.toString()));
    mvc.perform(get(URL).param("page", "2").param("size", "2").with(asUser(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(0)))
        .andExpect(jsonPath("$.data.totalElements").value(3));
  }

  @Test
  void 같은_시각의_결과도_페이지간_중복없이_정렬한다() {
    UUID user = UUID.randomUUID();
    var ids = new HashSet<UUID>();
    for (int i = 0; i < 3; i++) ids.add(seed(user, "결과", 1));
    entityManager.clear();
    var actual = new HashSet<UUID>();
    for (int page = 0; page < 3; page++) {
      var result = repository.findByUserId(user, page, 1);
      var id = result.content().get(0).getId();
      assertThat(repository.findByUserId(user, page, 1).content().get(0).getId()).isEqualTo(id);
      actual.add(id);
    }
    assertThat(actual).isEqualTo(ids);
  }

  @Test
  void 결과가_없으면_기본_페이지와_빈_목록을_반환한다() throws Exception {
    seed(UUID.randomUUID(), "다른 사용자", 1);
    mvc.perform(get(URL).with(asUser(UUID.randomUUID())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(0)))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.totalPages").value(0));
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "abc,20", "0,abc"})
  void 잘못된_페이지_요청은_400이다(String page, String size) throws Exception {
    mvc.perform(get(URL).param("page", page).param("size", size).with(asUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 본인_결과_상세를_조회한다() throws Exception {
    UUID user = UUID.randomUUID();
    UUID id = seed(user, "안녕하세요", 1);
    entityManager.clear();
    mvc.perform(get(URL + "/{id}", id).with(asUser(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recognitionId").value(id.toString()))
        .andExpect(jsonPath("$.data.recognizedText").value("안녕하세요"))
        .andExpect(jsonPath("$.data.modelUsed").value("BASE_ADAPTED"))
        .andExpect(jsonPath("$.data.confidence").value(0.8))
        .andExpect(jsonPath("$.data.userId").doesNotExist());
  }

  @Test
  void 타인_결과_상세는_403이다() throws Exception {
    UUID id = seed(UUID.randomUUID(), "비공개", 1);
    mvc.perform(get(URL + "/{id}", id).with(asUser(UUID.randomUUID())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"))
        .andExpect(jsonPath("$.data.recognizedText").doesNotExist());
  }

  @Test
  void 없는_결과는_404이다() throws Exception {
    mvc.perform(get(URL + "/{id}", UUID.randomUUID()).with(asUser(UUID.randomUUID())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void 잘못된_UUID는_400이다() throws Exception {
    mvc.perform(get(URL + "/not-a-uuid").with(asUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 인증하지_않으면_목록과_상세에_접근할_수_없다() throws Exception {
    mvc.perform(get(URL)).andExpect(status().is4xxClientError());
    mvc.perform(get(URL + "/{id}", UUID.randomUUID())).andExpect(status().is4xxClientError());
  }

  private UUID seed(UUID user, String text, int second) {
    var r =
        Recognition.reconstitute(
            UUID.randomUUID(),
            user,
            text,
            ModelType.BASE_ADAPTED,
            0.8,
            LocalDateTime.of(2026, 9, 23, 12, 0, second));
    return repository.save(r).getId();
  }

  private RequestPostProcessor asUser(UUID id) {
    return authentication(new UsernamePasswordAuthenticationToken(id, null, java.util.List.of()));
  }
}
