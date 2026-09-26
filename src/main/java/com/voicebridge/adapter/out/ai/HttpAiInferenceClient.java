package com.voicebridge.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!local")
public class HttpAiInferenceClient implements AiInferenceClient {

  private static final String STATUS_OK = "ok";
  private static final String STATUS_NO_SPEECH = "no_speech";

  private final RestClient restClient;
  private final String baseUrl;

  public HttpAiInferenceClient(
      RestClient.Builder restClientBuilder,
      @Value("${voicebridge.ai.http.base-url}") String baseUrl,
      @Value("${voicebridge.ai.http.connect-timeout-ms}") int connectTimeoutMs,
      @Value("${voicebridge.ai.http.read-timeout-ms}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    this.baseUrl = baseUrl;
  }

  @Override
  public RecognitionResult recognize(byte[] audioBytes, ModelType modelType, UUID userId) {
    AsrTranscribeResponse response =
        restClient
            .post()
            .uri(baseUrl + "/v1/asr/transcribe?user_id={userId}", userId)
            .contentType(MediaType.valueOf("audio/wav"))
            .body(audioBytes)
            .retrieve()
            .body(AsrTranscribeResponse.class);

    if (response == null || response.text() == null) {
      throw new IllegalStateException("AI 서버 응답에 text가 없습니다 — 계약 위반입니다.");
    }
    if (!STATUS_OK.equals(response.status()) && !STATUS_NO_SPEECH.equals(response.status())) {
      throw new IllegalStateException("AI 서버가 알 수 없는 status를 반환했습니다: " + response.status());
    }

    return new RecognitionResult(response.text(), response.score());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AsrTranscribeResponse(String status, String text, Double score) {}
}
