package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.persistence.PhraseJpaRepository;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.out.AudioPlaybackUrlPort;
import com.voicebridge.port.out.PhraseRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TtsEnginePort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서비스와 DB는 실제 빈을 쓰고 클로바·S3만 가짜로 둔다. 검증 규칙이 도메인에 있어서 서비스를 가짜로 두면 400·409를 HTTP 수준에서 확인할 수 없다.
// 가짜 클로바 테스트는 실제 클로바 동작을 보장하지 않는다. 실제 키로 하는 왕복 확인은 따로 수동으로 한다.
// 같은 컨텍스트를 쓰는 다른 테스트와 기본 문구가 섞이지 않게 시작할 때 문구 테이블을 비운다.
@SpringBootTest
@AutoConfigureMockMvc
class PhraseIntegrationTest {

  private static final String URL = "/api/v1/phrases";

  @Autowired MockMvc mvc;
  @Autowired PhraseRepositoryPort phrases;
  @Autowired PhraseJpaRepository phraseJpaRepository;
  @MockitoBean TtsEnginePort ttsEngine;
  @MockitoBean StoragePort storage;
  @MockitoBean AudioPlaybackUrlPort playbackUrls;

  private final UUID me = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();
  private Phrase greeting;

  @BeforeEach
  void setUp() {
    phraseJpaRepository.deleteAllInBatch();
    greeting = phrases.save(Phrase.createDefault(PhraseCategory.HOSPITAL, "접수하고 싶어요."));
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
  void 목록은_카테고리_순서대로_기본_문구_다음에_내_문구가_오고_다른_사람_문구는_없다() throws Exception {
    Phrase restaurant = phrases.save(Phrase.createDefault(PhraseCategory.RESTAURANT, "메뉴판 주세요."));
    // 기본 문구보다 먼저 추가했어도 같은 카테고리 안에서는 기본 문구 다음에 온다
    Phrase mine = phrases.save(Phrase.createForUser(me, PhraseCategory.HOSPITAL, "예약했어요."));
    phrases.save(Phrase.createForUser(other, PhraseCategory.HOSPITAL, "다른 사람 문구"));

    mvc.perform(as(me, get(URL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].id").value(greeting.getId().toString()))
        .andExpect(jsonPath("$.data[0].category").value("HOSPITAL"))
        .andExpect(jsonPath("$.data[0].text").value("접수하고 싶어요."))
        .andExpect(jsonPath("$.data[0].isDefault").value(true))
        .andExpect(jsonPath("$.data[1].id").value(mine.getId().toString()))
        .andExpect(jsonPath("$.data[1].isDefault").value(false))
        .andExpect(jsonPath("$.data[2].id").value(restaurant.getId().toString()))
        .andExpect(jsonPath("$.data[2].category").value("RESTAURANT"));
  }

  @Test
  void 카테고리로_거를_수_있고_없는_카테고리는_400이다() throws Exception {
    phrases.save(Phrase.createDefault(PhraseCategory.RESTAURANT, "메뉴판 주세요."));
    phrases.save(Phrase.createForUser(me, PhraseCategory.RESTAURANT, "포장이요."));
    phrases.save(Phrase.createForUser(other, PhraseCategory.RESTAURANT, "다른 사람 문구"));

    mvc.perform(as(me, get(URL).param("category", "RESTAURANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].text").value("메뉴판 주세요."))
        .andExpect(jsonPath("$.data[1].text").value("포장이요."));
    mvc.perform(as(me, get(URL).param("category", "FOOD")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 문구를_추가하면_201이고_내_목록에_보인다() throws Exception {
    mvc.perform(addRequest(me, "DAILY", "  물 좀 주세요.  "))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").isNotEmpty())
        .andExpect(jsonPath("$.data.category").value("DAILY"))
        .andExpect(jsonPath("$.data.text").value("물 좀 주세요."))
        .andExpect(jsonPath("$.data.isDefault").value(false));

    mvc.perform(as(me, get(URL).param("category", "DAILY")))
        .andExpect(jsonPath("$.data[0].text").value("물 좀 주세요."));
    // 다른 사람 목록에는 없다
    mvc.perform(as(other, get(URL).param("category", "DAILY")))
        .andExpect(jsonPath("$.data.length()").value(0));
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
  void 같은_카테고리에_이미_추가한_문구는_409다() throws Exception {
    mvc.perform(addRequest(me, "DAILY", "물 좀 주세요.")).andExpect(status().isCreated());

    mvc.perform(addRequest(me, "DAILY", "물 좀 주세요."))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_PHRASE"));
    // 카테고리가 다르면 추가된다
    mvc.perform(addRequest(me, "RESTAURANT", "물 좀 주세요.")).andExpect(status().isCreated());
    // 기본 문구와 같은 텍스트도 추가된다(내 문구끼리만 막는다)
    mvc.perform(addRequest(me, "HOSPITAL", "접수하고 싶어요.")).andExpect(status().isCreated());
    // 다른 사람은 같은 문구를 추가할 수 있다
    mvc.perform(addRequest(other, "DAILY", "물 좀 주세요.")).andExpect(status().isCreated());
  }

  @Test
  void 상한에_닿으면_409다() throws Exception {
    for (int i = 0; i < Phrase.MAX_CUSTOM_PHRASES_PER_USER; i++) {
      phrases.save(Phrase.createForUser(me, PhraseCategory.DAILY, "문구 " + i));
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
    Phrase mine = phrases.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));

    mvc.perform(as(me, delete(URL + "/" + mine.getId()))).andExpect(status().isNoContent());

    assertThat(phrases.findById(mine.getId())).isEmpty();
  }

  @Test
  void 다른_사람의_문구는_삭제할_수_없다() throws Exception {
    Phrase theirs = phrases.save(Phrase.createForUser(other, PhraseCategory.DAILY, "다른 사람 문구"));

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
  void UUID가_아닌_주소로는_삭제하지_않고_404다() throws Exception {
    mvc.perform(as(me, delete(URL + "/suggestions")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    assertThat(phrases.findById(greeting.getId())).isPresent();
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

  // ---- 말하기

  private static final String AUDIO_KEY = "recordings/0b6f1c2e-1111-4222-8333-944445555666.mp3";

  private void clovaAndS3Work() {
    // 앞에서 예외를 던지게 해 둔 목도 다시 정할 수 있게 doReturn을 쓴다(when(...)은 목을 한 번 호출한다).
    doReturn(new byte[] {1, 2, 3}).when(ttsEngine).synthesize(anyString());
    when(storage.upload(any(), anyString())).thenReturn(AUDIO_KEY);
    when(playbackUrls.createPhraseUrl(eq(AUDIO_KEY), any()))
        .thenReturn("https://example.test/audio.mp3?signature=test");
  }

  @Test
  void 두_번_눌러도_합성은_한_번이고_같은_음성을_재생한다() throws Exception {
    clovaAndS3Work();

    for (int i = 0; i < 2; i++) {
      mvc.perform(as(me, post(URL + "/" + greeting.getId() + "/speak")))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.data.id").value(greeting.getId().toString()))
          .andExpect(
              jsonPath("$.data.audioUrl").value("https://example.test/audio.mp3?signature=test"));
    }

    verify(ttsEngine, times(1)).synthesize("접수하고 싶어요.");
    verify(storage, times(1)).upload(any(), anyString());
    assertThat(phrases.findById(greeting.getId()).orElseThrow().getAudioStorageKey())
        .isEqualTo(AUDIO_KEY);
  }

  @Test
  void 기본_문구의_음성은_다른_사용자도_다시_합성하지_않고_쓴다() throws Exception {
    clovaAndS3Work();

    mvc.perform(as(me, post(URL + "/" + greeting.getId() + "/speak"))).andExpect(status().isOk());
    mvc.perform(as(other, post(URL + "/" + greeting.getId() + "/speak")))
        .andExpect(status().isOk());

    verify(ttsEngine, times(1)).synthesize(anyString());
  }

  @Test
  void 클로바가_실패하면_503이고_아무것도_저장하지_않는다() throws Exception {
    when(ttsEngine.synthesize(anyString())).thenThrow(new IllegalStateException("clova down"));

    mvc.perform(as(me, post(URL + "/" + greeting.getId() + "/speak")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("TTS_UNAVAILABLE"));

    verify(storage, never()).upload(any(), anyString());
    assertThat(phrases.findById(greeting.getId()).orElseThrow().hasAudio()).isFalse();

    // 다음에 누르면 다시 합성을 시도한다
    clovaAndS3Work();
    mvc.perform(as(me, post(URL + "/" + greeting.getId() + "/speak"))).andExpect(status().isOk());
    assertThat(phrases.findById(greeting.getId()).orElseThrow().hasAudio()).isTrue();
  }

  @Test
  void 다른_사람의_문구는_말하기를_거절하고_합성하지_않는다() throws Exception {
    Phrase theirs = phrases.save(Phrase.createForUser(other, PhraseCategory.DAILY, "다른 사람 문구"));

    mvc.perform(as(me, post(URL + "/" + theirs.getId() + "/speak")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"));

    verifyNoInteractions(ttsEngine, storage, playbackUrls);
  }

  @Test
  void 내_문구는_말할_수_있다() throws Exception {
    clovaAndS3Work();
    Phrase mine = phrases.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));

    mvc.perform(as(me, post(URL + "/" + mine.getId() + "/speak"))).andExpect(status().isOk());

    verify(ttsEngine).synthesize("물 좀 주세요.");
  }

  @Test
  void 없는_문구를_말하면_404다() throws Exception {
    mvc.perform(as(me, post(URL + "/" + UUID.randomUUID() + "/speak")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    verifyNoInteractions(ttsEngine);
  }

  @Test
  void 로그인하지_않으면_말하기도_401이고_합성하지_않는다() throws Exception {
    mvc.perform(post(URL + "/" + greeting.getId() + "/speak")).andExpect(status().isUnauthorized());
    verifyNoInteractions(ttsEngine);
  }

  @Test
  void 음성이_있는_문구를_삭제하면_음성도_지운다() throws Exception {
    clovaAndS3Work();
    Phrase mine = phrases.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));
    mvc.perform(as(me, post(URL + "/" + mine.getId() + "/speak"))).andExpect(status().isOk());

    mvc.perform(as(me, delete(URL + "/" + mine.getId()))).andExpect(status().isNoContent());

    verify(storage).delete(AUDIO_KEY);
  }
}
