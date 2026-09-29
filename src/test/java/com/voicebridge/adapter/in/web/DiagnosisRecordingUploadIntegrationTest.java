package com.voicebridge.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase;
import com.voicebridge.port.in.UploadDiagnosisRecordingUseCase.UploadResult;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 서비스 단위 테스트로는 요청의 모양(multipart 필드명, 상태 코드, 응답 JSON)이 명세서와 맞는지 알 수 없다.
// 필드명이 명세서(audioFile)와 달라 프론트 요청이 전부 실패할 뻔한 일이 있어 HTTP 계약만 따로 고정한다.
@SpringBootTest
@AutoConfigureMockMvc
class DiagnosisRecordingUploadIntegrationTest {

  @Autowired MockMvc mvc;
  @MockitoBean UploadDiagnosisRecordingUseCase uploadDiagnosisRecordingUseCase;

  private final UUID userId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID sentenceId = UUID.randomUUID();

  private String url() {
    return "/api/v1/diagnosis-sessions/" + sessionId + "/recordings";
  }

  private MockMultipartFile wav(String partName) {
    return new MockMultipartFile(partName, "recording.wav", "audio/wav", new byte[] {1, 2, 3});
  }

  @Test
  void 명세서의_audioFile_필드로_보내면_202와_접수_결과를_돌려준다() throws Exception {
    UUID recordingId = UUID.randomUUID();
    when(uploadDiagnosisRecordingUseCase.upload(any()))
        .thenReturn(new UploadResult(recordingId, sentenceId, "PROCESSING"));

    mvc.perform(
            multipart(url())
                .file(wav("audioFile"))
                .param("sentenceId", sentenceId.toString())
                .with(asUser(userId)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.recordingId").value(recordingId.toString()))
        .andExpect(jsonPath("$.data.sentenceId").value(sentenceId.toString()))
        .andExpect(jsonPath("$.data.status").value("PROCESSING"));
  }

  @Test
  void 명세서와_다른_필드명으로_보내면_거절한다() throws Exception {
    mvc.perform(
            multipart(url())
                .file(wav("file"))
                .param("sentenceId", sentenceId.toString())
                .with(asUser(userId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("필수 요청 값이 없습니다: audioFile"));
  }

  @Test
  void 문장_ID를_빼먹으면_거절한다() throws Exception {
    mvc.perform(multipart(url()).file(wav("audioFile")).with(asUser(userId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("필수 요청 값이 없습니다: sentenceId"));
  }

  private RequestPostProcessor asUser(UUID id) {
    return authentication(new UsernamePasswordAuthenticationToken(id, null, List.of()));
  }
}
