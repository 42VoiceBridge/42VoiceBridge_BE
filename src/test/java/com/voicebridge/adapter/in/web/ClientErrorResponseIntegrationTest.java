package com.voicebridge.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.port.out.TokenProviderPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 클라이언트 요청 실수는 500(서버 고장)이 아니라 4xx와 공통 응답 형식으로 돌려준다. 인증을 통과한 요청으로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ClientErrorResponseIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired TokenProviderPort tokens;

  private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + tokens.createAccessToken(UUID.randomUUID()));
  }

  @Test
  void 없는_주소는_404() throws Exception {
    mvc.perform(authorized(get("/api/v1/diagnosis-sesions")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void 받지_않는_메서드는_405와_허용_메서드() throws Exception {
    mvc.perform(authorized(delete("/api/v1/diagnosis-sessions")))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", "POST"))
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void 읽을_수_없는_JSON은_400() throws Exception {
    mvc.perform(
            authorized(
                post("/api/v1/users/me/recommendations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{bad")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 받지_않는_Content_Type은_415() throws Exception {
    mvc.perform(
            authorized(
                post("/api/v1/users/me/recommendations")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("x")))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }
}
