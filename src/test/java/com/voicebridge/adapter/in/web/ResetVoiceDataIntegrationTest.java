package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.port.out.StoragePort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 실제 DB(테스트 H2)에 두 사용자의 음성 데이터를 테이블마다 넣고, 한 사용자만 지워지는지 본다. 저장소(S3)만 가짜로 둔다.
// 행은 SQL로 넣는다: 도메인 규칙을 거치면 테이블마다 준비할 것이 너무 많고, 여기서 확인할 것은 "어느 행이 지워지는가"다.
@SpringBootTest
@AutoConfigureMockMvc
class ResetVoiceDataIntegrationTest {

  private static final String URL = "/api/v1/users/me/voice-data";
  private static final List<String> TABLES =
      List.of(
          "tts_requests",
          "confirmations",
          "recognitions",
          "recordings",
          "diagnosis_session_sentences",
          "diagnosis_sessions",
          "jamo_error_snapshot_tokens",
          "jamo_error_snapshots",
          "shown_prompts",
          "personalization_recordings",
          "personalization_jobs",
          "personalization_adapters",
          "phrases",
          "users");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean StoragePort storage;

  private final UUID me = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();
  private final Timestamp now = Timestamp.valueOf(LocalDateTime.now());

  @BeforeEach
  void setUp() {
    TABLES.forEach(table -> jdbc.update("delete from " + table));
    seed(me, "me");
    seed(other, "other");
  }

  // 사용자마다 테이블별로 한 행씩, 파일 key는 "{tag}-..."로 구분할 수 있게 넣는다.
  private void seed(UUID user, String tag) {
    jdbc.update(
        "insert into users (id, email, nickname, provider, created_at) values (?, ?, ?, 'LOCAL', ?)",
        user,
        tag + "@example.com",
        tag,
        now);
    UUID recognition = UUID.randomUUID();
    UUID confirmation = UUID.randomUUID();
    jdbc.update(
        "insert into recognitions (id, user_id, recognized_text, model_used, created_at)"
            + " values (?, ?, '안녕하세요', 'BASE_ADAPTED', ?)",
        recognition,
        user,
        now);
    jdbc.update(
        "insert into confirmations (id, recognition_id, user_id, confirmed_text, valid, created_at)"
            + " values (?, ?, ?, '안녕하세요', true, ?)",
        confirmation,
        recognition,
        user,
        now);
    jdbc.update(
        "insert into tts_requests (id, confirmation_id, idempotency_key, status, audio_url,"
            + " created_at) values (?, ?, ?, 'COMPLETED', ?, ?)",
        UUID.randomUUID(),
        confirmation,
        UUID.randomUUID(),
        tag + "-tts.mp3",
        now);
    UUID session = UUID.randomUUID();
    jdbc.update(
        "insert into diagnosis_sessions (id, user_id, status, created_at) values (?, ?, 'ANALYZED', ?)",
        session,
        user,
        now);
    jdbc.update(
        "insert into diagnosis_session_sentences (session_id, sentence_order, sentence_ids)"
            + " values (?, 0, ?)",
        session,
        UUID.randomUUID());
    jdbc.update(
        "insert into recordings (id, session_id, user_id, s3_path, status, created_at)"
            + " values (?, ?, ?, ?, 'DONE', ?)",
        UUID.randomUUID(),
        session,
        user,
        tag + "-diagnosis.wav",
        now);
    jdbc.update(
        "insert into jamo_error_snapshots (user_id, min_support, sessions_used, pairs_used)"
            + " values (?, 10, 1, 1)",
        user);
    jdbc.update(
        "insert into jamo_error_snapshot_tokens (user_id, token_order, token, errors, sample_count,"
            + " error_rate) values (?, 0, 'ㄱ', 1, 10, 0.1)",
        user);
    jdbc.update(
        "insert into shown_prompts (id, user_id, seed, shown_at) values (?, ?, 1, ?)",
        UUID.randomUUID(),
        user,
        now);
    UUID job = UUID.randomUUID();
    jdbc.update(
        "insert into personalization_recordings (id, user_id, storage_key, use_for_training,"
            + " source_sample_rate, source_channels, sample_count, created_at)"
            + " values (?, ?, ?, true, 16000, 1, 16000, ?)",
        UUID.randomUUID(),
        user,
        tag + "-personalization.wav",
        now);
    jdbc.update(
        "insert into personalization_jobs (id, user_id, status, training_recording_count)"
            + " values (?, ?, 'COMPLETED', 1)",
        job,
        user);
    jdbc.update(
        "insert into personalization_adapters (id, user_id, job_id, status,"
            + " training_recording_count) values (?, ?, ?, 'CANDIDATE', 1)",
        UUID.randomUUID(),
        user,
        job);
    jdbc.update(
        "insert into phrases (id, owner_id, category, text, text_sha256, created_at)"
            + " values (?, ?, 'DAILY', ?, ?, ?)",
        UUID.randomUUID(),
        user,
        tag + " 문구",
        "%064d".formatted(tag.length()),
        now);
  }

  private MockHttpServletRequestBuilder reset(UUID userId) {
    return delete(URL)
        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())));
  }

  private Map<String, Integer> rowsOf(UUID user) {
    return Map.ofEntries(
        Map.entry("recognitions", count("recognitions", "user_id", user)),
        Map.entry("confirmations", count("confirmations", "user_id", user)),
        Map.entry(
            "tts_requests",
            jdbc.queryForObject(
                "select count(*) from tts_requests t join confirmations c"
                    + " on t.confirmation_id = c.id where c.user_id = ?",
                Integer.class,
                user)),
        Map.entry("recordings", count("recordings", "user_id", user)),
        Map.entry("diagnosis_sessions", count("diagnosis_sessions", "user_id", user)),
        Map.entry("jamo_error_snapshots", count("jamo_error_snapshots", "user_id", user)),
        Map.entry(
            "jamo_error_snapshot_tokens", count("jamo_error_snapshot_tokens", "user_id", user)),
        Map.entry("shown_prompts", count("shown_prompts", "user_id", user)),
        Map.entry(
            "personalization_recordings", count("personalization_recordings", "user_id", user)),
        Map.entry("personalization_jobs", count("personalization_jobs", "user_id", user)),
        Map.entry("personalization_adapters", count("personalization_adapters", "user_id", user)));
  }

  private int count(String table, String column, UUID user) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where " + column + " = ?", Integer.class, user);
  }

  @Test
  void 내_음성_데이터와_파일만_지우고_계정과_문구는_남긴다() throws Exception {
    mvc.perform(reset(me)).andExpect(status().isNoContent());

    assertThat(rowsOf(me).values()).allMatch(n -> n == 0);
    assertThat(
            jdbc.queryForObject("select count(*) from diagnosis_session_sentences", Integer.class))
        .isEqualTo(1); // 남은 하나는 다른 사용자 세션의 문장
    assertThat(count("users", "id", me)).isEqualTo(1);
    assertThat(count("phrases", "owner_id", me)).isEqualTo(1);

    verify(storage).delete("me-tts.mp3");
    verify(storage).delete("me-diagnosis.wav");
    verify(storage).delete("me-personalization.wav");
  }

  @Test
  void 다른_사용자의_데이터와_파일은_지우지_않는다() throws Exception {
    mvc.perform(reset(me)).andExpect(status().isNoContent());

    assertThat(rowsOf(other).values()).allMatch(n -> n == 1);
    verify(storage, never()).delete(org.mockito.ArgumentMatchers.startsWith("other-"));
  }

  @Test
  void 다시_불러도_204이고_지울_파일이_없다() throws Exception {
    mvc.perform(reset(me)).andExpect(status().isNoContent());
    org.mockito.Mockito.clearInvocations(storage);

    mvc.perform(reset(me)).andExpect(status().isNoContent());

    verify(storage, never()).delete(anyString());
  }

  @Test
  void 파일_삭제가_실패해도_DB는_지워지고_204다() throws Exception {
    doThrow(new IllegalStateException("s3 down")).when(storage).delete(any());

    mvc.perform(reset(me)).andExpect(status().isNoContent());

    assertThat(rowsOf(me).values()).allMatch(n -> n == 0);
  }

  @Test
  void 로그인하지_않으면_401이고_아무것도_지우지_않는다() throws Exception {
    mvc.perform(delete(URL)).andExpect(status().isUnauthorized());

    assertThat(rowsOf(me).values()).allMatch(n -> n == 1);
    verifyNoInteractions(storage);
  }
}
