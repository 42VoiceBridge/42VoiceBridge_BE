package com.voicebridge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import com.voicebridge.port.out.RecognitionRepositoryPort;
import com.voicebridge.port.out.TokenProviderPort;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecognitionUploadIntegrationTest {
  private static final String URL = "/api/v1/recognitions";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TokenProviderPort tokenProvider;
  @Autowired RecognitionRepositoryPort repository;
  @Autowired EntityManager entityManager;
  @MockitoBean AiInferenceClient aiInferenceClient;

  @Test
  void uploadPersistsResultForAuthenticatedUserAndExposesItThroughQueries() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID suppliedUserId = UUID.randomUUID();
    byte[] audio = wavBytes();
    when(aiInferenceClient.recognize(any(), eq(ModelType.BASE_ADAPTED), eq(userId)))
        .thenReturn(new AiInferenceClient.RecognitionResult("안녕하세요", null));

    var response =
        mvc.perform(
                multipart(URL)
                    .file(new MockMultipartFile("audioFile", "speech.wav", "audio/wav", audio))
                    .param("userId", suppliedUserId.toString())
                    .header("Authorization", token(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recognizedText").value("안녕하세요"))
            .andExpect(jsonPath("$.data.modelUsed").value("BASE_ADAPTED"))
            .andExpect(jsonPath("$.data.confidence").value(nullValue()))
            .andReturn();
    UUID id =
        UUID.fromString(
            objectMapper
                .readTree(response.getResponse().getContentAsString())
                .path("data")
                .path("recognitionId")
                .asText());
    entityManager.flush();
    entityManager.clear();
    assertThat(repository.findById(id).orElseThrow().getUserId()).isEqualTo(userId);
    verify(aiInferenceClient).recognize(eq(audio), eq(ModelType.BASE_ADAPTED), eq(userId));

    mvc.perform(get(URL + "/{id}", id).header("Authorization", token(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recognitionId").value(id.toString()))
        .andExpect(jsonPath("$.data.recognizedText").value("안녕하세요"));
    mvc.perform(get(URL).header("Authorization", token(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].recognitionId").value(id.toString()));
    mvc.perform(get(URL + "/{id}", id).header("Authorization", token(suppliedUserId)))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"file", "missing"})
  void missingOrWrongPartReturns400WithoutCallingAi(String partName) throws Exception {
    var request = multipart(URL);
    request.header("Authorization", token(UUID.randomUUID()));
    if (!partName.equals("missing")) {
      request.file(wav(partName));
    }
    mvc.perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.message").value("필수 요청 값이 없습니다: audioFile"));
    verifyNoInteractions(aiInferenceClient);
  }

  @Test
  void emptyFileReturns400WithoutCallingAi() throws Exception {
    mvc.perform(
            multipart(URL)
                .file(new MockMultipartFile("audioFile", "empty.wav", "audio/wav", new byte[0]))
                .header("Authorization", token(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    verifyNoInteractions(aiInferenceClient);
  }

  @Test
  void unauthenticatedUploadIsRejectedBeforeCallingAi() throws Exception {
    mvc.perform(multipart(URL).file(wav("audioFile"))).andExpect(status().is4xxClientError());
    verifyNoInteractions(aiInferenceClient);
  }

  @Test
  void aiFailureReturns503WithoutPersistingRecognition() throws Exception {
    UUID userId = UUID.randomUUID();
    when(aiInferenceClient.recognize(any(), any(), eq(userId)))
        .thenThrow(new IllegalStateException("AI unavailable"));
    mvc.perform(multipart(URL).file(wav("audioFile")).header("Authorization", token(userId)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("AI_INFERENCE_UNAVAILABLE"));
    assertThat(repository.findByUserId(userId, 0, 20).totalElements()).isZero();
  }

  @Test
  void noRecognizedTextIsAValidStoredResult() throws Exception {
    UUID userId = UUID.randomUUID();
    when(aiInferenceClient.recognize(any(), any(), eq(userId)))
        .thenReturn(new AiInferenceClient.RecognitionResult("", null));
    mvc.perform(multipart(URL).file(wav("audioFile")).header("Authorization", token(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recognizedText").value(""))
        .andExpect(jsonPath("$.data.confidence").value(nullValue()));
    assertThat(repository.findByUserId(userId, 0, 20).totalElements()).isEqualTo(1);
  }

  @Test
  void fileReadFailureReturns500WithoutCallingAi() throws Exception {
    var unreadable =
        new MockMultipartFile("audioFile", "speech.wav", "audio/wav", wavBytes()) {
          @Override
          public byte[] getBytes() throws IOException {
            throw new IOException("read failed");
          }
        };
    mvc.perform(multipart(URL).file(unreadable).header("Authorization", token(UUID.randomUUID())))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    verifyNoInteractions(aiInferenceClient);
  }

  private String token(UUID userId) {
    return "Bearer " + tokenProvider.createAccessToken(userId);
  }

  private MockMultipartFile wav(String partName) {
    return new MockMultipartFile(partName, "speech.wav", "audio/wav", wavBytes());
  }

  // Half a second of PCM16 mono 16 kHz silence; AI is mocked in these HTTP/DB tests.
  private byte[] wavBytes() {
    int dataSize = 8000 * 2;
    return ByteBuffer.allocate(44 + dataSize)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".getBytes(StandardCharsets.US_ASCII))
        .putInt(36 + dataSize)
        .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(16000)
        .putInt(32000)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes(StandardCharsets.US_ASCII))
        .putInt(dataSize)
        .put(new byte[dataSize])
        .array();
  }
}
