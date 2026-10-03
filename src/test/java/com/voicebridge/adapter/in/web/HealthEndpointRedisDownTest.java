package com.voicebridge.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Redis에 연결할 수 없으면 DOWN(503). 배포 검사가 로그인이 안 되는 서버를 정상으로 보지 않게 한다. */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointRedisDownTest {

  @DynamicPropertySource
  static void unreachableRedis(DynamicPropertyRegistry registry) {
    // 아무것도 듣고 있지 않은 포트
    registry.add("spring.data.redis.host", () -> "127.0.0.1");
    registry.add("spring.data.redis.port", () -> 1);
    registry.add("spring.data.redis.timeout", () -> "1s");
    registry.add("spring.data.redis.connect-timeout", () -> "1s");
  }

  @Autowired MockMvc mvc;

  @Test
  void Redis에_연결할_수_없으면_DOWN과_503을_돌려준다() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }
}
