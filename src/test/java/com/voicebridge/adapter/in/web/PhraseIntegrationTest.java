package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.persistence.PhraseJpaRepository;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.out.PhraseRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서비스와 DB는 실제 빈을 쓴다. 검증 규칙이 도메인에 있어서 서비스를 가짜로 두면 400·409를 HTTP 수준에서 확인할 수 없다.
// 같은 컨텍스트를 쓰는 다른 테스트와 기본 문구가 섞이지 않게 시작할 때 문구 테이블을 비운다.
@SpringBootTest
@AutoConfigureMockMvc
class PhraseIntegrationTest {

  private static final String URL = "/api/v1/phrases";

  @Autowired MockMvc mvc;
  @Autowired PhraseRepositoryPort phrases;
  @Autowired PhraseJpaRepository phraseJpaRepository;

  private final UUID me = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();
  private Phrase greeting;

  @BeforeEach
  void setUp() {
    phraseJpaRepository.deleteAllInBatch();
    greeting = phrases.save(Phrase.createDefault(PhraseCategory.GREETING, "안녕하세요."));
  }

  private MockHttpServletRequestBuilder as(UUID userId, MockHttpServletRequestBuilder request) {
    return request.with(
        authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())));
  }

  private MockHttpServletRequestBuilder addRequest(UUID userId, String category, String text) {
    return as(userId, post(URL))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"category\": \"" + category + "\", \"text\": \"" + text + "\"}");
  }

  @Test
  void 목록은_모든_카테고리를_순서대로_보여주고_기본_문구_다음에_내_문구가_온다() throws Exception {
    phrases.save(Phrase.createCustom(me, PhraseCategory.GREETING, "반가워요."));
    phrases.save(Phrase.createCustom(other, PhraseCategory.GREETING, "다른 사람 문구"));

    mvc.perform(as(me, get(URL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categories.length()").value(PhraseCategory.values().length))
        .andExpect(jsonPath("$.data.categories[0].category").value("GREETING"))
        .andExpect(jsonPath("$.data.categories[0].label").value("인사"))
        .andExpect(jsonPath("$.data.categories[0].phrases.length()").value(2))
        .andExpect(
            jsonPath("$.data.categories[0].phrases[0].phraseId").value(greeting.getId().toString()))
        .andExpect(jsonPath("$.data.categories[0].phrases[0].text").value("안녕하세요."))
        .andExpect(jsonPath("$.data.categories[0].phrases[0].custom").value(false))
        .andExpect(jsonPath("$.data.categories[0].phrases[1].text").value("반가워요."))
        .andExpect(jsonPath("$.data.categories[0].phrases[1].custom").value(true))
        // 문구가 없는 카테고리도 빈 목록으로 온다(그 카테고리에 추가할 수 있게)
        .andExpect(jsonPath("$.data.categories[4].category").value("EMERGENCY"))
        .andExpect(jsonPath("$.data.categories[4].phrases.length()").value(0));
  }

  @Test
  void 문구를_추가하면_201이고_내_목록에_보인다() throws Exception {
    mvc.perform(addRequest(me, "DAILY", "  물 좀 주세요.  "))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.phraseId").isNotEmpty())
        .andExpect(jsonPath("$.data.category").value("DAILY"))
        .andExpect(jsonPath("$.data.text").value("물 좀 주세요."))
        .andExpect(jsonPath("$.data.custom").value(true));

    mvc.perform(as(me, get(URL)))
        .andExpect(jsonPath("$.data.categories[2].category").value("DAILY"))
        .andExpect(jsonPath("$.data.categories[2].phrases[0].text").value("물 좀 주세요."));
    // 다른 사람 목록에는 없다
    mvc.perform(as(other, get(URL)))
        .andExpect(jsonPath("$.data.categories[2].phrases.length()").value(0));
  }

  @Test
  void 잘못된_입력은_400이다() throws Exception {
    mvc.perform(addRequest(me, "DAILY", "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    mvc.perform(addRequest(me, "FOOD", "물 좀 주세요."))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    mvc.perform(addRequest(me, "DAILY", "가".repeat(Phrase.MAX_TEXT_LENGTH + 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    mvc.perform(addRequest(me, "DAILY", "물\\n주세요"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    mvc.perform(as(me, post(URL)).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    assertThat(phrases.countCustomByOwner(me)).isZero();
  }

  @Test
  void 이미_있는_문구는_409다() throws Exception {
    mvc.perform(addRequest(me, "DAILY", "물 좀 주세요.")).andExpect(status().isCreated());

    // 내 문구와 같으면 카테고리가 달라도 중복이다
    mvc.perform(addRequest(me, "HEALTH", "물 좀 주세요."))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PHRASE_DUPLICATED"));
    // 기본 문구와 같아도 중복이다
    mvc.perform(addRequest(me, "GREETING", "안녕하세요."))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PHRASE_DUPLICATED"));
    // 다른 사람은 같은 문구를 추가할 수 있다
    mvc.perform(addRequest(other, "DAILY", "물 좀 주세요.")).andExpect(status().isCreated());
  }

  @Test
  void 상한에_닿으면_409다() throws Exception {
    for (int i = 0; i < Phrase.MAX_CUSTOM_PHRASES_PER_USER; i++) {
      phrases.save(Phrase.createCustom(me, PhraseCategory.DAILY, "문구 " + i));
    }

    mvc.perform(addRequest(me, "DAILY", "하나 더"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PHRASE_LIMIT_EXCEEDED"));
    assertThat(phrases.countCustomByOwner(me)).isEqualTo(Phrase.MAX_CUSTOM_PHRASES_PER_USER);
    // 기본 문구는 상한에 세지 않으므로 다른 사용자는 영향이 없다
    mvc.perform(addRequest(other, "DAILY", "하나 더")).andExpect(status().isCreated());
  }

  @Test
  void 내_문구를_삭제하면_204이고_목록에서_사라진다() throws Exception {
    Phrase mine = phrases.save(Phrase.createCustom(me, PhraseCategory.DAILY, "물 좀 주세요."));

    mvc.perform(as(me, delete(URL + "/" + mine.getId()))).andExpect(status().isNoContent());

    assertThat(phrases.findById(mine.getId())).isEmpty();
  }

  @Test
  void 다른_사람의_문구는_삭제할_수_없다() throws Exception {
    Phrase theirs = phrases.save(Phrase.createCustom(other, PhraseCategory.DAILY, "다른 사람 문구"));

    mvc.perform(as(me, delete(URL + "/" + theirs.getId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"));

    assertThat(phrases.findById(theirs.getId())).isPresent();
  }

  @Test
  void 기본_문구는_삭제할_수_없다() throws Exception {
    mvc.perform(as(me, delete(URL + "/" + greeting.getId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"))
        .andExpect(jsonPath("$.error.message").value("기본 문구는 삭제할 수 없습니다."));

    assertThat(phrases.findById(greeting.getId())).isPresent();
  }

  @Test
  void 없는_문구는_404다() throws Exception {
    mvc.perform(as(me, delete(URL + "/" + UUID.randomUUID())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void 로그인하지_않으면_401이다() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\": \"DAILY\", \"text\": \"물\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete(URL + "/" + greeting.getId())).andExpect(status().isUnauthorized());
    assertThat(phrases.countCustomByOwner(me)).isZero();
  }
}
