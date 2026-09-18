package com.voicebridge.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void swaggerUi는_인증없이_접근할수있다() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html").with(csrf())).andExpect(status().isOk());
  }

  @Test
  void apiDocs는_인증없이_접근할수있다() throws Exception {
    mockMvc.perform(get("/v3/api-docs").with(csrf())).andExpect(status().isOk());
  }

  @Test
  void 기존_보호_엔드포인트는_swagger_permitAll에_영향받지않고_여전히_인증이_필요하다() throws Exception {
    mockMvc.perform(get("/api/v1/users/me").with(csrf())).andExpect(status().isForbidden());
  }
}
