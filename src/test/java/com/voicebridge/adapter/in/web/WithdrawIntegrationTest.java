package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.domain.user.User;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TokenProviderPort;
import com.voicebridge.port.out.UserRepositoryPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 사용자·문구·음성 데이터는 실제 DB(테스트 H2)에 두고, 저장소(S3)와 리프레시 토큰 저장소(Redis)만 가짜로 둔다.
// 음성 데이터의 테이블별 삭제는 ResetVoiceDataIntegrationTest가 자세히 보므로, 여기서는 대표로 인식 결과 한 줄만 넣어 함께 지워지는지 본다.
@SpringBootTest
@AutoConfigureMockMvc
class WithdrawIntegrationTest {

  private static final String URL = "/api/v1/users/me/withdraw";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepositoryPort users;
  @Autowired PasswordEncoderPort passwordEncoder;
  @Autowired TokenProviderPort tokenProvider;
  @MockitoBean StoragePort storage;
  @MockitoBean RefreshTokenStorePort refreshTokenStore;

  private User me;
  private User kakao;
  private User other;
  private final Timestamp now = Timestamp.valueOf(LocalDateTime.now());

  @BeforeEach
  void setUp() {
    for (String table : List.of("recognitions", "phrases", "users")) {
      jdbc.update("delete from " + table);
    }
    me = users.save(User.createLocal("me@example.com", passwordEncoder.encode("my-password"), "나"));
    kakao = users.save(User.createFromKakao("kakao-1", "kakao@example.com", "카카오"));
    other =
        users.save(
            User.createLocal("other@example.com", passwordEncoder.encode("other-password"), "남"));
    for (User user : List.of(me, kakao, other)) {
      seed(user.getId(), user.getNickname());
    }
  }

  private void seed(UUID user, String tag) {
    jdbc.update(
        "insert into recognitions (id, user_id, recognized_text, model_used, created_at)"
            + " values (?, ?, '안녕하세요', 'BASE_ADAPTED', ?)",
        UUID.randomUUID(),
        user,
        now);
    jdbc.update(
        "insert into phrases (id, owner_id, category, text, text_sha256, audio_storage_key,"
            + " created_at) values (?, ?, 'DAILY', ?, ?, ?, ?)",
        UUID.randomUUID(),
        user,
        tag + " 문구",
        "%064d".formatted(tag.hashCode() & 0xffff),
        tag + "-phrase.mp3",
        now);
  }

  private MockHttpServletRequestBuilder withdraw(UUID userId, String body) {
    var request =
        post(URL)
            .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())));
    return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private int rows(String table, String column, UUID user) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where " + column + " = ?", Integer.class, user);
  }

  private void assertAllDataOf(UUID user, int expected) {
    assertThat(rows("users", "id", user)).isEqualTo(expected);
    assertThat(rows("phrases", "owner_id", user)).isEqualTo(expected);
    assertThat(rows("recognitions", "user_id", user)).isEqualTo(expected);
  }

  @Test
  void 이메일_회원은_비밀번호가_맞으면_계정과_모든_데이터를_지운다() throws Exception {
    mvc.perform(withdraw(me.getId(), "{\"password\":\"my-password\"}"))
        .andExpect(status().isNoContent());

    assertAllDataOf(me.getId(), 0);
    verify(storage).delete("나-phrase.mp3");
    verify(refreshTokenStore).revoke(me.getId());
  }

  @Test
  void 카카오_회원은_비밀번호_없이_탈퇴한다() throws Exception {
    mvc.perform(withdraw(kakao.getId(), null)).andExpect(status().isNoContent());

    assertAllDataOf(kakao.getId(), 0);
    verify(refreshTokenStore).revoke(kakao.getId());
  }

  @Test
  void 비밀번호가_틀리면_400이고_아무것도_지우지_않는다() throws Exception {
    mvc.perform(withdraw(me.getId(), "{\"password\":\"wrong-password\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));

    assertAllDataOf(me.getId(), 1);
    verifyNoInteractions(storage);
    verify(refreshTokenStore, never()).revoke(any());
  }

  @Test
  void 이메일_회원이_비밀번호를_보내지_않으면_400이다() throws Exception {
    mvc.perform(withdraw(me.getId(), null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    mvc.perform(withdraw(me.getId(), "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    assertAllDataOf(me.getId(), 1);
  }

  @Test
  void 다른_사용자의_데이터와_파일은_지우지_않는다() throws Exception {
    mvc.perform(withdraw(me.getId(), "{\"password\":\"my-password\"}"))
        .andExpect(status().isNoContent());

    assertAllDataOf(other.getId(), 1);
    assertAllDataOf(kakao.getId(), 1);
    verify(storage, never()).delete(startsWith("남-"));
  }

  @Test
  void 탈퇴한_뒤에는_남은_액세스_토큰도_401이다() throws Exception {
    String token = tokenProvider.createAccessToken(me.getId());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mvc.perform(withdraw(me.getId(), "{\"password\":\"my-password\"}"))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_EXPIRED"));
    // 없는 사용자 이름으로 데이터를 만들 수 없다
    mvc.perform(
            post("/api/v1/phrases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"DAILY\",\"text\":\"물 주세요\"}"))
        .andExpect(status().isUnauthorized());
    assertThat(rows("phrases", "owner_id", me.getId())).isZero();
  }

  @Test
  void 로그인하지_않으면_401이고_아무것도_지우지_않는다() throws Exception {
    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
        .andExpect(status().isUnauthorized());

    assertAllDataOf(me.getId(), 1);
    verifyNoInteractions(storage);
  }
}
