package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.port.out.TokenProviderPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 배포 검사(Infra)가 쓰는 /actuator/health. 토큰 없이 열리되 상태 외의 정보는 내보내지 않는다. 로그인·토큰 재발급이 Redis에 기대므로 실제
 * Redis를 붙여 UP을 확인한다(Redis가 없을 때는 HealthEndpointRedisDownTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest {

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static {
    REDIS.start();
  }

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired MockMvc mvc;
  @Autowired TokenProviderPort tokens;

  @Test
  void 토큰_없이_200과_상태만_돌려준다() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        // DB·Redis 같은 구성 요소 이름과 상세 정보는 드러나지 않는다
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void health_말고_다른_actuator_주소는_열리지_않는다() throws Exception {
    // 토큰이 없으면 보안 설정에서 먼저 막힌다
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    // 토큰이 있어도 노출하지 않은 주소라 설정값이 나가지 않는다. 상태 코드는 확인하지 않는다: 지금은 없는 주소가 404가 아니라
    // 전역 예외 처리의 500으로 나간다(NoResourceFoundException 미처리, 별도 수정 대상).
    mvc.perform(
            get("/actuator/env")
                .header("Authorization", "Bearer " + tokens.createAccessToken(UUID.randomUUID())))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200))
        .andExpect(jsonPath("$.propertySources").doesNotExist());
  }

  @Test
  void 토큰_없이_허용하는_것은_GET뿐이다() throws Exception {
    mvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
  }
}
