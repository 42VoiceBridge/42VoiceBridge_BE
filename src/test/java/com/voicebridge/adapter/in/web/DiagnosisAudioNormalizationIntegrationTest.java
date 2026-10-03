package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.domain.diagnosis.DiagnosisSession;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.TokenProviderPort;
import com.voicebridge.support.AudioFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 진단 녹음 업로드가 실제 FFmpeg 변환을 거치는지 끝에서 끝으로 확인한다. 저장소와 AI만 대역으로 두고 변환기는 진짜를 쓴다. FFmpeg가 필요해서 일반 테스트에서
 * 빼고 audioIntegrationTest(CI 포함)에서 돈다.
 */
@Tag("ffmpeg")
@SpringBootTest
@AutoConfigureMockMvc
class DiagnosisAudioNormalizationIntegrationTest {

  @TempDir Path directory;
  @Autowired MockMvc mvc;
  @Autowired TokenProviderPort tokens;
  @Autowired DiagnosisSessionRepositoryPort sessions;
  @MockitoBean StoragePort storage;
  @MockitoBean AiInferenceClient ai;

  private final UUID userId = UUID.randomUUID();
  private final UUID sentenceId = UUID.randomUUID();
  private UUID sessionId;

  @BeforeEach
  void setUp() {
    sessionId = sessions.save(DiagnosisSession.start(userId, List.of(sentenceId))).getId();
    when(storage.upload(any(), any())).thenReturn("recordings/converted.wav");
  }

  private ResultActions upload(byte[] audio) throws Exception {
    return mvc.perform(
        multipart("/api/v1/diagnosis-sessions/{sessionId}/recordings", sessionId)
            .file(new MockMultipartFile("audioFile", "recording.webm", "audio/webm", audio))
            .param("sentenceId", sentenceId.toString())
            .header("Authorization", "Bearer " + tokens.createAccessToken(userId)));
  }

  @Test
  void 브라우저_WebM을_WAV로_바꿔_저장하고_AI에도_WAV를_보낸다() throws Exception {
    byte[] webm = AudioFixtures.encoded(directory, "webm", "libopus", 2);

    upload(webm).andExpect(status().isAccepted());

    ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
    verify(storage).upload(stored.capture(), eq("recording.wav"));
    assertThat(AudioFixtures.assertCanonicalWav(stored.getValue())).isBetween(7800, 8500);

    // 인식은 커밋 뒤 비동기로 돈다
    ArgumentCaptor<byte[]> recognized = ArgumentCaptor.forClass(byte[].class);
    verify(ai, timeout(5_000))
        .recognize(recognized.capture(), eq(ModelType.BASE_ADAPTED), eq(userId));
    assertThat(recognized.getValue()).isEqualTo(stored.getValue()).isNotEqualTo(webm);
  }

  @Test
  void 너무_짧은_녹음은_400_AUDIO_TOO_SHORT로_바로_거절하고_저장하지_않는다() throws Exception {
    upload(AudioFixtures.wav(16000, 1, 1600))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUDIO_TOO_SHORT"));
    verifyNoInteractions(storage, ai);
  }

  @Test
  void 너무_긴_녹음은_400_AUDIO_TOO_LONG으로_바로_거절하고_저장하지_않는다() throws Exception {
    upload(AudioFixtures.wav(16000, 1, 16000 * 31))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUDIO_TOO_LONG"));
    verifyNoInteractions(storage, ai);
  }

  @Test
  void 읽을_수_없는_파일은_400_AUDIO_INVALID로_바로_거절하고_저장하지_않는다() throws Exception {
    upload(new byte[] {1, 2, 3})
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUDIO_INVALID"));
    verifyNoInteractions(storage, ai);
  }
}
