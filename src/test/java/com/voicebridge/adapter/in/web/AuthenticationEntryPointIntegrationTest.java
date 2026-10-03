package com.voicebridge.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.auth.JwtTokenProvider;
import com.voicebridge.port.out.TokenProviderPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 토큰이 없거나 거절된 요청은 401과 공통 응답 형식으로, 토큰이 유효하거나 인증이 필요 없는 요청은 그대로 통과하는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationEntryPointIntegrationTest {

  // 인증이 필요한 아무 엔드포인트. 인증을 통과하면 없는 세션이라 404가 나온다.
  private static final String PROTECTED = "/api/v1/diagnosis-sessions/{sessionId}";

  @Autowired MockMvc mvc;
  @Autowired TokenProviderPort tokens;

  @Value("${voicebridge.jwt.secret}")
  String secret;

  private String expiredToken() {
    return new JwtTokenProvider(secret, -60, 60).createAccessToken(UUID.randomUUID());
  }

  @Test
  void 토큰이_없으면_401_AUTH_REQUIRED() throws Exception {
    mvc.perform(get(PROTECTED, UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
  }

  @Test
  void 만료된_토큰이면_401_AUTH_TOKEN_EXPIRED로_재발급을_안내한다() throws Exception {
    mvc.perform(
            get(PROTECTED, UUID.randomUUID()).header("Authorization", "Bearer " + expiredToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_EXPIRED"))
        .andExpect(jsonPath("$.error.message").value("인증 토큰이 만료되었습니다."));
  }

  @Test
  void 다른_키로_서명한_토큰은_401로_거절한다() throws Exception {
    String forged =
        new JwtTokenProvider("another-secret-key-for-forged-tokens-32chars-min", 3600, 3600)
            .createAccessToken(UUID.randomUUID());

    mvc.perform(get(PROTECTED, UUID.randomUUID()).header("Authorization", "Bearer " + forged))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_EXPIRED"))
        .andExpect(jsonPath("$.error.message").value("유효하지 않은 토큰입니다."));
  }

  @Test
  void Bearer_형식이_아니면_토큰이_없는_것과_같다() throws Exception {
    mvc.perform(get(PROTECTED, UUID.randomUUID()).header("Authorization", "Basic abc"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
  }

  @Test
  void 유효한_토큰이면_인증을_통과한다() throws Exception {
    mvc.perform(
            get(PROTECTED, UUID.randomUUID())
                .header("Authorization", "Bearer " + tokens.createAccessToken(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void 인증이_필요_없는_요청은_만료된_토큰을_붙여도_막지_않는다() throws Exception {
    // 프론트는 토큰 재발급·로그인 요청에 만료된 토큰을 그대로 붙여 보낼 수 있다
    mvc.perform(
            post("/api/v1/auth/login")
                .header("Authorization", "Bearer " + expiredToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nobody@example.com\", \"password\": \"wrong-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void CORS_사전_요청은_토큰_없이도_통과한다() throws Exception {
    // 브라우저는 다른 출처로 Authorization 헤더를 보내기 전에 토큰 없이 OPTIONS를 먼저 보낸다. 여기서 401이 나면 모든 요청이 막힌다.
    mvc.perform(
            options(PROTECTED, UUID.randomUUID())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }
}
