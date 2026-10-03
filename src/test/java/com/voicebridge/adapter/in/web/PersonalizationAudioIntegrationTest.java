package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.adapter.out.persistence.PersonalizationRecordingJpaRepository;
import com.voicebridge.domain.recommendation.ShownPrompt;
import com.voicebridge.port.out.ShownPromptRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.support.AudioFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Tag("ffmpeg")
@SpringBootTest
@AutoConfigureMockMvc
class PersonalizationAudioIntegrationTest {
  @TempDir Path directory;
  @Autowired MockMvc mvc;
  @Autowired ShownPromptRepositoryPort prompts;
  @Autowired PersonalizationRecordingJpaRepository recordings;
  @MockitoBean StoragePort storage;

  @Test
  void wavWebmAndM4aBecomeCanonicalWavWithPersistedProvenance() throws Exception {
    byte[][] sources = {
      AudioFixtures.wav(48000, 1, 24000),
      AudioFixtures.encoded(directory, "webm", "libopus", 1),
      AudioFixtures.encoded(directory, "m4a", "aac", 1)
    };
    for (int i = 0; i < sources.length; i++) {
      UUID user = UUID.randomUUID();
      ShownPrompt shown =
          prompts
              .saveAll(
                  List.of(
                      ShownPrompt.create(
                          user, "p-" + i, "문장", "random", "v1", i, "pool-v1", "pool-sha")))
              .get(0);
      var meta =
          new MockMultipartFile(
              "metadata",
              "metadata.json",
              "application/json",
              ("{\"shownPromptId\":\""
                      + shown.getId()
                      + "\",\"promptId\":\"p-"
                      + i
                      + "\",\"storeAudio\":true}")
                  .getBytes());
      var audio = new MockMultipartFile("audioFile", "misleading.wav", "audio/wav", sources[i]);
      mvc.perform(
              multipart("/api/v1/personalization/recordings")
                  .file(meta)
                  .file(audio)
                  .with(
                      authentication(
                          new UsernamePasswordAuthenticationToken(user, null, List.of()))))
          .andExpect(status().isCreated());
      var saved =
          recordings.findAll().stream()
              .filter(r -> r.getShownPromptId().equals(shown.getId()))
              .findFirst()
              .orElseThrow();
      assertThat(saved.getSourceSha256()).isNotBlank();
      assertThat(saved.getWavSha256()).isNotBlank();
      assertThat(saved.getSampleCount()).isBetween(7800, 8500);
    }
    var bytes = ArgumentCaptor.forClass(byte[].class);
    verify(storage, org.mockito.Mockito.times(3)).uploadAt(anyString(), bytes.capture());
    bytes.getAllValues().forEach(AudioFixtures::assertCanonicalWav);
  }
}
