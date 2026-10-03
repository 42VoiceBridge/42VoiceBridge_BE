package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.persistence.PersonalizationRecordingJpaRepository;
import com.voicebridge.domain.personalization.PersonalizationRecording;
import com.voicebridge.domain.recommendation.ShownPrompt;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.AudioNormalizationPort.Metadata;
import com.voicebridge.port.out.AudioNormalizationPort.NormalizedAudio;
import com.voicebridge.port.out.PersonalizationRecordingRepositoryPort;
import com.voicebridge.port.out.ShownPromptRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PersonalizationRecordingIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ShownPromptRepositoryPort prompts;
  @Autowired PersonalizationRecordingJpaRepository recordings;
  @Autowired PersonalizationRecordingRepositoryPort recordingPort;
  @MockitoBean AudioNormalizationPort normalizer;
  @MockitoBean StoragePort storage;

  private MockMultipartFile metadata(String body) {
    return new MockMultipartFile("metadata", "metadata.json", "application/json", body.getBytes());
  }

  private MockMultipartFile audio() {
    return new MockMultipartFile("audioFile", "audio.wav", "audio/wav", new byte[] {1, 2, 3});
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser(
      UUID user) {
    return authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
  }

  private String body(UUID id, String promptId, String consent) {
    return "{\"shownPromptId\":\""
        + id
        + "\",\"promptId\":\""
        + promptId
        + "\",\"storeAudio\":"
        + consent
        + ",\"useForTraining\":false}";
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.NullAndEmptySource
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"text/plain", "invalid", "application/json;charset=\"unterminated"})
  void rejectsInvalidMetadataContentType(String contentType) throws Exception {
    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(
                    new MockMultipartFile(
                        "metadata", "metadata.json", contentType, "{}".getBytes()))
                .file(audio())
                .with(asUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(
            jsonPath("$.error.message").value("metadata의 Content-Type은 application/json이어야 합니다."));
    org.mockito.Mockito.verifyNoInteractions(normalizer, storage);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource("invalidMetadata")
  void explainsInvalidMetadata(String body, String message) throws Exception {
    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(metadata(body))
                .file(audio())
                .with(asUser(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.message").value(message));
    org.mockito.Mockito.verifyNoInteractions(normalizer, storage);
  }

  static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidMetadata() {
    String id = UUID.randomUUID().toString();
    return java.util.stream.Stream.of(
        org.junit.jupiter.params.provider.Arguments.of("", "metadata가 비어 있습니다."),
        org.junit.jupiter.params.provider.Arguments.of(
            " ".repeat(4097), "metadata는 4096바이트 이하여야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of("{}{}", "metadata는 올바른 JSON 객체여야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of("{", "metadata는 올바른 JSON 객체여야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of("[]", "metadata는 올바른 JSON 객체여야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of("null", "metadata는 올바른 JSON 객체여야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of("{}", "shownPromptId는 유효한 UUID 문자열이어야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"shownPromptId\":1}", "shownPromptId는 유효한 UUID 문자열이어야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"shownPromptId\":\"1-1-1-1-1\"}", "shownPromptId는 유효한 UUID 문자열이어야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"shownPromptId\":\"" + id + "\",\"promptId\":1}", "promptId는 비어 있지 않은 문자열이어야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"storeAudio\":null}", "storeAudio는 boolean 값이어야 합니다."),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"useForTraining\":1}", "useForTraining는 boolean 값이어야 합니다."));
  }

  @Test
  void explainsMissingConsentAndMismatchedPrompt() throws Exception {
    UUID user = UUID.randomUUID();
    ShownPrompt shown =
        prompts
            .saveAll(
                List.of(
                    ShownPrompt.create(
                        user, "p-validation", "문장", "random", "v1", 1, "pool-v1", "pool-sha")))
            .get(0);
    for (String consent : new String[] {"false", "true"}) {
      mvc.perform(
              multipart("/api/v1/personalization/recordings")
                  .file(metadata(body(shown.getId(), "wrong", consent)))
                  .file(audio())
                  .with(asUser(user)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
          .andExpect(
              jsonPath("$.error.message")
                  .value(
                      consent.equals("false")
                          ? "녹음 저장에 동의해야 합니다: storeAudio=true"
                          : "promptId가 추천 기록의 문장과 일치하지 않습니다."));
    }
    org.mockito.Mockito.verifyNoInteractions(normalizer, storage);
  }

  @Test
  void rejectsMissingConsentEmptyAudioAndMissingRecommendation() throws Exception {
    UUID user = UUID.randomUUID();
    String metadata = "{\"shownPromptId\":\"" + UUID.randomUUID() + "\",\"promptId\":\"p\"}";
    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(metadata(metadata))
                .file(audio())
                .with(asUser(user)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("녹음 저장에 동의해야 합니다: storeAudio=true"));
    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(metadata(body(UUID.randomUUID(), "p", "true")))
                .file(new MockMultipartFile("audioFile", new byte[0]))
                .with(asUser(user)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("audioFile이 비어 있습니다."));
    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(metadata(body(UUID.randomUUID(), "p", "true")))
                .file(audio())
                .with(asUser(user)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    org.mockito.Mockito.verifyNoInteractions(normalizer, storage);
  }

  @Test
  void schedulingIsDisabledInTests() {
    assertThat(
            context.getBeansOfType(
                org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
                    .class))
        .isEmpty();
  }

  @Autowired org.springframework.context.ApplicationContext context;

  @Test
  void uploadsTheExactRecommendationAndPersistsMetadata() throws Exception {
    UUID user = UUID.randomUUID();
    ShownPrompt shown =
        prompts
            .saveAll(
                List.of(
                    ShownPrompt.create(
                        user, "p-1", "원문", "random", "v1", 1, "pool-v1", "pool-sha")))
            .get(0);
    when(normalizer.normalize(any()))
        .thenReturn(
            new NormalizedAudio(
                new byte[] {4, 5},
                new Metadata("wav", "pcm_s16le", 16000, 1, 1000, "source-hash", "wav-hash", "v1")));

    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(
                    new MockMultipartFile(
                        "metadata",
                        "metadata.json",
                        "application/json;charset=UTF-8",
                        body(shown.getId(), "p-1", "true").getBytes()))
                .file(audio())
                .with(asUser(user)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("UPLOADED"));

    var saved =
        recordings.findAll().stream()
            .filter(r -> r.getShownPromptId().equals(shown.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(saved.getPromptText()).isEqualTo("원문");
    assertThat(saved.getPromptId()).isEqualTo("p-1");
    assertThat(saved.getStatus()).isEqualTo("UPLOADED");
    assertThat(saved.getWavSha256()).isEqualTo("wav-hash");
    assertThat(saved.isUseForTraining()).isFalse();
    verify(storage).uploadAt(saved.getStorageKey(), new byte[] {4, 5});
  }

  @Test
  void rejectsAnotherUsersRecommendationAndInvalidConsent() throws Exception {
    UUID owner = UUID.randomUUID();
    ShownPrompt shown =
        prompts
            .saveAll(
                List.of(
                    ShownPrompt.create(
                        owner, "p-2", "문장", "random", "v1", 2, "pool-v1", "pool-sha")))
            .get(0);
    String url = "/api/v1/personalization/recordings";
    mvc.perform(
            multipart(url)
                .file(metadata(body(shown.getId(), "p-2", "true")))
                .file(audio())
                .with(asUser(UUID.randomUUID())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"));
    mvc.perform(
            multipart(url)
                .file(metadata(body(shown.getId(), "p-2", "\"false\"")))
                .file(audio())
                .with(asUser(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void failedStorageDoesNotPublishRecording() throws Exception {
    UUID owner = UUID.randomUUID();
    ShownPrompt shown =
        prompts
            .saveAll(
                List.of(
                    ShownPrompt.create(
                        owner, "p-3", "문장", "random", "v1", 3, "pool-v1", "pool-sha")))
            .get(0);
    when(normalizer.normalize(any()))
        .thenReturn(
            new NormalizedAudio(
                new byte[] {4, 5},
                new Metadata("wav", "pcm_s16le", 16000, 1, 1000, "source", "wav", "v1")));
    doThrow(new RuntimeException("storage unavailable")).when(storage).uploadAt(anyString(), any());
    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(metadata(body(shown.getId(), "p-3", "true")))
                .file(audio())
                .with(asUser(owner)))
        .andExpect(status().isInternalServerError());
    assertThat(
            recordings.findAll().stream()
                .noneMatch(r -> r.getShownPromptId().equals(shown.getId())))
        .isTrue();
  }

  @Test
  void deletionRevokesOwnRecordingAndRejectsAnotherUser() throws Exception {
    UUID owner = UUID.randomUUID();
    ShownPrompt shown =
        prompts
            .saveAll(
                List.of(
                    ShownPrompt.create(
                        owner, "p-4", "문장", "random", "v1", 4, "pool-v1", "pool-sha")))
            .get(0);
    when(normalizer.normalize(any()))
        .thenReturn(
            new NormalizedAudio(
                new byte[] {4, 5},
                new Metadata("wav", "pcm_s16le", 16000, 1, 1000, "source", "wav", "v1")));
    mvc.perform(
            multipart("/api/v1/personalization/recordings")
                .file(metadata(body(shown.getId(), "p-4", "true")))
                .file(audio())
                .with(asUser(owner)))
        .andExpect(status().isCreated());
    var recording =
        recordings.findAll().stream()
            .filter(r -> r.getShownPromptId().equals(shown.getId()))
            .findFirst()
            .orElseThrow();
    String url = "/api/v1/personalization/recordings/" + recording.getId();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .with(asUser(UUID.randomUUID())))
        .andExpect(status().isForbidden());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .with(asUser(owner)))
        .andExpect(status().isAccepted());
    assertThat(recordings.findById(recording.getId())).isEmpty();
    verify(storage).delete(recording.getStorageKey());
  }

  @Test
  void cleanupClaimPreventsLateUploadFromBecomingVisible() {
    UUID id = UUID.randomUUID();
    LocalDateTime old = LocalDateTime.now().minusMinutes(10);
    var recording =
        new PersonalizationRecording(
            id,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "p-5",
            "문장",
            "personalization/" + id + ".wav",
            false,
            "v1",
            old,
            "wav",
            "pcm_s16le",
            16000,
            1,
            1000,
            "source",
            "wav",
            "v1",
            com.voicebridge.domain.personalization.PersonalizationRecordingStatus.PREPARING,
            old,
            null,
            null,
            null,
            null,
            null);
    recordingPort.prepare(recording);
    assertThat(recordingPort.claimPendingForCleanup(id, LocalDateTime.now().minusMinutes(5)))
        .isTrue();
    assertThatThrownBy(() -> recordingPort.markUploaded(id))
        .isInstanceOf(IllegalStateException.class);
    assertThat(recordings.findById(id).orElseThrow().getStatus()).isEqualTo("CLEANUP_PENDING");
  }

  @Test
  void trainingIsUnavailableWithoutAiContract() throws Exception {
    mvc.perform(post("/api/v1/personalization/train").with(asUser(UUID.randomUUID())))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("TRAINING_UNAVAILABLE"));
  }
}
