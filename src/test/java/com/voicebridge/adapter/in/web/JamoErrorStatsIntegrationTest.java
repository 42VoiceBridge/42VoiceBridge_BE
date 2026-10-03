package com.voicebridge.adapter.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.domain.diagnosis.JamoErrorStat;
import com.voicebridge.port.in.GetJamoErrorStatsUseCase;
import com.voicebridge.port.in.GetJamoErrorStatsUseCase.StatsResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JamoErrorStatsIntegrationTest {

  private static final String URL = "/api/v1/users/me/jamo-error-stats";

  @Autowired MockMvc mvc;
  @MockitoBean GetJamoErrorStatsUseCase getJamoErrorStatsUseCase;

  @Test
  void 로그인한_사용자_본인의_통계를_돌려준다() throws Exception {
    UUID userId = UUID.randomUUID();
    when(getJamoErrorStatsUseCase.getStats(userId))
        .thenReturn(
            new StatsResult(
                "jamo-err-v1",
                20,
                3,
                15,
                List.of(
                    new JamoErrorStat("ㅈ", "INITIAL", 12, 20, 0.6, "OK"),
                    new JamoErrorStat("ㅆ", "FINAL", 3, 7, null, "INSUFFICIENT_DATA"))));

    mvc.perform(
            get(URL)
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.metricVersion").value("jamo-err-v1"))
        .andExpect(jsonPath("$.data.sessionsUsed").value(3))
        .andExpect(jsonPath("$.data.tokens[0].position").value("INITIAL"))
        .andExpect(jsonPath("$.data.tokens[0].errorRate").value(0.6))
        // 표본 부족은 0.0이 아니라 null로 나가야 한다
        .andExpect(jsonPath("$.data.tokens[1].errorRate").value(nullValue()))
        .andExpect(jsonPath("$.data.tokens[1].status").value("INSUFFICIENT_DATA"));
  }

  @Test
  void 로그인하지_않으면_조회할_수_없다() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }
}
