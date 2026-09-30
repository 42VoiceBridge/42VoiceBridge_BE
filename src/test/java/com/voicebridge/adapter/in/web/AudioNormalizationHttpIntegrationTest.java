package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.TokenProviderPort;
import com.voicebridge.support.AudioFixtures;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Tag("ffmpeg")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AudioNormalizationHttpIntegrationTest {
  @TempDir Path directory;
  @Autowired MockMvc mvc;
  @Autowired TokenProviderPort tokens;
  @MockitoBean AiInferenceClient ai;

  @Test
  void multipartWebmIsDecodedBeforeInferenceEvenWithAWrongFileNameAndMime() throws Exception {
    UUID userId = UUID.randomUUID();
    byte[] webm = AudioFixtures.encoded(directory, "webm", "libopus", 2);
    when(ai.recognize(any(), any(), eq(userId)))
        .thenReturn(new AiInferenceClient.RecognitionResult("물 좀 주세요", null));
    mvc.perform(
            multipart("/api/v1/recognitions")
                .file(new MockMultipartFile("audioFile", "../wrong.wav", "audio/wav", webm))
                .header("Authorization", "Bearer " + tokens.createAccessToken(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recognizedText").value("물 좀 주세요"));
    var bytes = ArgumentCaptor.forClass(byte[].class);
    verify(ai).recognize(bytes.capture(), any(), eq(userId));
    assertThat(AudioFixtures.assertCanonicalWav(bytes.getValue())).isBetween(7800, 8500);
    assertThat(bytes.getValue()).isNotEqualTo(webm);
  }

  @Test
  void corruptAudioIs400AndNeverReachesAi() throws Exception {
    mvc.perform(
            multipart("/api/v1/recognitions")
                .file(
                    new MockMultipartFile(
                        "audioFile", "audio.wav", "audio/wav", new byte[] {1, 2, 3}))
                .header("Authorization", "Bearer " + tokens.createAccessToken(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    verifyNoInteractions(ai);
  }
}
