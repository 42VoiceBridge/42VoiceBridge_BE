package com.voicebridge.adapter.out.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class HttpAiInferenceClientTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private HttpAiInferenceClient client;
  private final UUID userId = UUID.randomUUID();
  private final byte[] audioBytes = {1, 2, 3, 4};

  @BeforeEach
  void setUp() {
    client = new HttpAiInferenceClient(RestClient.builder(), wireMock.baseUrl(), 3000, 10000);
  }

  @AfterEach
  void tearDown() {
    wireMock.resetAll();
  }

  @Test
  void status_ok이고_score가_null이면_정상_파싱한다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/v1/asr/transcribe"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"status": "ok", "text": "물 좀 주세요", "score": null}
                        """)));

    AiInferenceClient.RecognitionResult result =
        client.recognize(audioBytes, ModelType.BASE_ADAPTED, userId);

    assertThat(result.recognizedText()).isEqualTo("물 좀 주세요");
    assertThat(result.confidence()).isNull();
  }

  @Test
  void status_no_speech이면_예외_없이_정상_처리한다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/v1/asr/transcribe"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"status": "no_speech", "text": "", "score": null}
                        """)));

    AiInferenceClient.RecognitionResult result =
        client.recognize(audioBytes, ModelType.BASE_ADAPTED, userId);

    assertThat(result.recognizedText()).isEmpty();
    assertThat(result.confidence()).isNull();
  }

  @Test
  void text_필드가_없으면_예외를_던진다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/v1/asr/transcribe"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"status": "ok"}
                        """)));

    assertThatThrownBy(() -> client.recognize(audioBytes, ModelType.BASE_ADAPTED, userId))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 에러_응답이면_예외를_던진다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/v1/asr/transcribe"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"error": {"code": "INVALID_AUDIO", "message": "bad audio"}}
                        """)));

    assertThatThrownBy(() -> client.recognize(audioBytes, ModelType.BASE_ADAPTED, userId))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void 요청이_wav_content_type과_user_id_쿼리파라미터로_나간다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/v1/asr/transcribe"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"status": "ok", "text": "테스트", "score": null}
                        """)));

    client.recognize(audioBytes, ModelType.BASE_ADAPTED, userId);

    wireMock.verify(
        postRequestedFor(urlPathEqualTo("/v1/asr/transcribe"))
            .withQueryParam("user_id", equalTo(userId.toString()))
            .withHeader("Content-Type", containing("audio/wav")));
  }
}
