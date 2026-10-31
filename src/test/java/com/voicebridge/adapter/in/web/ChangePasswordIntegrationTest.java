package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.persistence.UserJpaRepository;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.UserRepositoryPort;
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

// 사용자 저장과 비밀번호 해시는 실제 빈을 쓴다(검증 규칙이 도메인에 있어 HTTP 수준에서 400·200을 확인한다). 리프레시 토큰 저장소(Redis)만 가짜로 둔다.
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordIntegrationTest {

  private static final String URL = "/api/v1/users/me/password";

  @Autowired MockMvc mvc;
  @Autowired UserRepositoryPort users;
  @Autowired UserJpaRepository userJpaRepository;
  @Autowired PasswordEncoderPort passwordEncoder;
  @MockitoBean RefreshTokenStorePort refreshTokenStore;

  private User local;
  private User kakao;

  @BeforeEach
  void setUp() {
    userJpaRepository.deleteAllInBatch();
    local =
        users.save(User.createLocal("me@example.com", passwordEncoder.encode("old-password"), "나"));
    kakao = users.save(User.createFromKakao("kakao-1", "kakao@example.com", "카카오"));
  }

  private MockHttpServletRequestBuilder change(UUID userId, String body) {
    return patch(URL)
        .with(authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String hashOf(UUID userId) {
    return users.findById(userId).orElseThrow().getPassword();
  }

  @Test
  void 현재_비밀번호가_맞으면_바꾸고_새_토큰을_준다() throws Exception {
    mvc.perform(
            change(
                local.getId(),
                "{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

    assertThat(passwordEncoder.matches("new-password", hashOf(local.getId()))).isTrue();
    assertThat(passwordEncoder.matches("old-password", hashOf(local.getId()))).isFalse();
    // 새 리프레시 토큰을 저장해 이전 토큰(다른 기기 포함)을 덮어쓴다
    verify(refreshTokenStore).save(eq(local.getId()), anyString());
  }

  @Test
  void 현재_비밀번호가_틀리면_400이고_바뀌지_않는다() throws Exception {
    mvc.perform(
            change(
                local.getId(),
                "{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));

    assertThat(passwordEncoder.matches("old-password", hashOf(local.getId()))).isTrue();
    verify(refreshTokenStore, never()).save(any(), any());
  }

  @Test
  void 새_비밀번호가_규칙에_맞지_않으면_400이고_바뀌지_않는다() throws Exception {
    for (String body :
        List.of(
            "{\"currentPassword\":\"old-password\",\"newPassword\":\"short\"}",
            "{\"currentPassword\":\"old-password\",\"newPassword\":\"old-password\"}",
            "{\"currentPassword\":\"old-password\"}",
            "{\"newPassword\":\"new-password\"}")) {
      mvc.perform(change(local.getId(), body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
    assertThat(passwordEncoder.matches("old-password", hashOf(local.getId()))).isTrue();
  }

  @Test
  void 카카오_계정은_400_PASSWORD_NOT_SUPPORTED다() throws Exception {
    mvc.perform(
            change(
                kakao.getId(),
                "{\"currentPassword\":\"anything1\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_NOT_SUPPORTED"));

    assertThat(hashOf(kakao.getId())).isNull();
  }

  @Test
  void 다른_사용자의_비밀번호는_바뀌지_않는다() throws Exception {
    User other =
        users.save(
            User.createLocal("other@example.com", passwordEncoder.encode("other-password"), "남"));

    mvc.perform(
            change(
                local.getId(),
                "{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isOk());

    assertThat(passwordEncoder.matches("other-password", hashOf(other.getId()))).isTrue();
  }

  @Test
  void 내_정보에_회원_종류가_나와_비밀번호_변경_버튼을_가릴_수_있다() throws Exception {
    mvc.perform(
            get("/api/v1/users/me")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(local.getId(), null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.provider").value("LOCAL"));
    mvc.perform(
            get("/api/v1/users/me")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(kakao.getId(), null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.provider").value("KAKAO"));
  }

  @Test
  void 로그인하지_않으면_401이다() throws Exception {
    mvc.perform(
            patch(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isUnauthorized());

    assertThat(passwordEncoder.matches("old-password", hashOf(local.getId()))).isTrue();
  }
}
