package com.voicebridge.adapter.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.domain.personalization.PersonalizationJob;
import com.voicebridge.domain.personalization.PersonalizationJobStatus;
import com.voicebridge.port.out.PersonalizationJobRepositoryPort;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalizationTrainingStatusIntegrationTest {

  private static final String URL = "/api/v1/personalization/train/{jobId}";
  private final MockMvc mockMvc;
  private final PersonalizationJobRepositoryPort repository;
  private final EntityManager entityManager;

  @Autowired
  PersonalizationTrainingStatusIntegrationTest(
      MockMvc mockMvc, PersonalizationJobRepositoryPort repository, EntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @ParameterizedTest
  @EnumSource(PersonalizationJobStatus.class)
  void 본인_작업의_각_상태와_시간_실패사유를_조회한다(PersonalizationJobStatus jobStatus) throws Exception {
    UUID userId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    LocalDateTime startedAt = LocalDateTime.of(2026, 9, 18, 12, 0);
    boolean finished =
        jobStatus == PersonalizationJobStatus.COMPLETED
            || jobStatus == PersonalizationJobStatus.FAILED;
    LocalDateTime completedAt = finished ? startedAt.plusMinutes(5) : null;
    String failureReason = jobStatus == PersonalizationJobStatus.FAILED ? "학습 처리 실패" : null;
    save(
        PersonalizationJob.reconstitute(
            jobId,
            userId,
            jobStatus,
            8,
            jobStatus == PersonalizationJobStatus.COMPLETED ? "v1" : null,
            jobStatus == PersonalizationJobStatus.COMPLETED ? "models/v1.pt" : null,
            failureReason,
            startedAt,
            completedAt));

    var response =
        mockMvc
            .perform(get(URL, jobId).with(asUser(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.error").value(nullValue()))
            .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.data.status").value(jobStatus.name()))
            .andExpect(jsonPath("$.data.startedAt").value("2026-09-18T12:00:00"))
            .andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.modelArtifactPath").doesNotExist());

    if (finished) {
      response.andExpect(jsonPath("$.data.completedAt").value("2026-09-18T12:05:00"));
    } else {
      response.andExpect(jsonPath("$.data.completedAt").value(nullValue()));
    }
    if (failureReason != null) {
      response.andExpect(jsonPath("$.data.failureReason").value(failureReason));
    } else {
      response.andExpect(jsonPath("$.data.failureReason").value(nullValue()));
    }
  }

  @Test
  void 없는_작업은_404를_반환한다() throws Exception {
    mockMvc
        .perform(get(URL, UUID.randomUUID()).with(asUser(UUID.randomUUID())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void 다른_사용자의_작업은_403을_반환한다() throws Exception {
    PersonalizationJob job = PersonalizationJob.create(UUID.randomUUID(), 8);
    save(job);

    mockMvc
        .perform(get(URL, job.getId()).with(asUser(UUID.randomUUID())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"));
  }

  @Test
  void 잘못된_UUID는_400을_반환한다() throws Exception {
    mockMvc
        .perform(get(URL, "not-a-uuid").with(asUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 인증되지_않은_요청은_거절한다() throws Exception {
    PersonalizationJob job = PersonalizationJob.create(UUID.randomUUID(), 8);
    save(job);

    mockMvc.perform(get(URL, job.getId())).andExpect(status().isForbidden());
  }

  private void save(PersonalizationJob job) {
    repository.save(job);
    entityManager.flush();
    entityManager.clear();
  }

  private RequestPostProcessor asUser(UUID userId) {
    return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }
}
