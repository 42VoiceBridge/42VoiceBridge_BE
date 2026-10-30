package com.voicebridge.adapter.out.stt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

/**
 * CSR과의 계약 테스트. 요청·응답 모양은 공식 문서 기준이다. WireMock 통과가 실제 CSR 동작(인식 품질, 키 권한)을 보장하지 않으므로 실제 키로 따로
 * 확인한다(부록 4.7).
 */
class NaverClovaSpeechAdapterTest {

  private static final String PATH = "/recog/v1/stt";
  private static final byte[] WAV = {82, 73, 70, 70, 1, 2, 3, 4};

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private NaverClovaSpeechAdapter adapter(String keyId, String key, Duration timeout) {
    return new NaverClovaSpeechAdapter(
        RestClient.builder(), wireMock.baseUrl() + PATH, keyId, key, timeout);
  }

  private NaverClovaSpeechAdapter adapter() {
    return adapter("test-key-id", "test-key", Duration.ofSeconds(2));
  }

  private void respond(int status, String body) {
    wireMock.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private static void assertSttUnavailable(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STT_UNAVAILABLE));
  }

  @AfterEach
  void tearDown() {
    wireMock.resetAll();
  }

  @Test
  void 음성을_바이너리로_보내고_인식한_텍스트를_돌려준다() {
    respond(200, "{\"text\":\" 약은 식후에 드세요. \"}");

    assertThat(adapter().transcribe(WAV)).isEqualTo("약은 식후에 드세요.");

    wireMock.verify(
        postRequestedFor(urlPathEqualTo(PATH))
            .withQueryParam("lang", equalTo("Kor"))
            .withHeader("X-NCP-APIGW-API-KEY-ID", equalTo("test-key-id"))
            .withHeader("X-NCP-APIGW-API-KEY", equalTo("test-key"))
            .withHeader("Content-Type", equalTo("application/octet-stream"))
            .withRequestBody(binaryEqualTo(WAV)));
  }

  @Test
  void 인식_결과가_비면_503이다() {
    respond(200, "{\"text\":\"  \"}");
    assertSttUnavailable(() -> adapter().transcribe(WAV));

    respond(200, "{}");
    assertSttUnavailable(() -> adapter().transcribe(WAV));
  }

  @Test
  void 오류_응답은_503이다() {
    for (int status : new int[] {400, 401, 429, 500, 503}) {
      respond(status, "{\"error\":{\"errorCode\":\"E\",\"message\":\"m\"}}");
      assertSttUnavailable(() -> adapter().transcribe(WAV));
    }
  }

  @Test
  void 응답_형식이_깨지면_503이다() {
    respond(200, "not json");
    assertSttUnavailable(() -> adapter().transcribe(WAV));
  }

  @Test
  void 시간이_초과되면_503이다() {
    wireMock.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"text\":\"늦은 응답\"}")
                    .withFixedDelay(1500)));

    assertSttUnavailable(
        () -> adapter("test-key-id", "test-key", Duration.ofMillis(300)).transcribe(WAV));
  }

  @Test
  void 키가_없으면_호출하지_않고_503이다() {
    assertSttUnavailable(() -> adapter("", "test-key", Duration.ofSeconds(2)).transcribe(WAV));
    assertSttUnavailable(() -> adapter("test-key-id", " ", Duration.ofSeconds(2)).transcribe(WAV));

    wireMock.verify(0, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void 빈_음성은_보내지_않는다() {
    assertThatThrownBy(() -> adapter().transcribe(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter().transcribe(null))
        .isInstanceOf(IllegalArgumentException.class);

    wireMock.verify(0, postRequestedFor(urlPathEqualTo(PATH)));
  }
}
