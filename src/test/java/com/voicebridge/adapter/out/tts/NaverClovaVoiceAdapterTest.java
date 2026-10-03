package com.voicebridge.adapter.out.tts;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class NaverClovaVoiceAdapterTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private NaverClovaVoiceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new NaverClovaVoiceAdapter(
            RestClient.builder(),
            wireMock.baseUrl() + "/tts-premium/v1/tts",
            "key-id",
            "key",
            "nara");
  }

  @AfterEach
  void tearDown() {
    wireMock.resetAll();
  }

  @Test
  void 성공하면_응답_바이너리를_그대로_반환한다() {
    byte[] mp3Bytes = {1, 2, 3, 4};
    wireMock.stubFor(
        post(urlPathEqualTo("/tts-premium/v1/tts"))
            .willReturn(aResponse().withHeader("Content-Type", "audio/mp3").withBody(mp3Bytes)));

    byte[] result = adapter.synthesize("물 좀 주세요");

    assertThat(result).isEqualTo(mp3Bytes);
  }

  @Test
  void 인증_헤더와_폼_필드가_계약대로_나간다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/tts-premium/v1/tts"))
            .willReturn(aResponse().withBody(new byte[] {1})));

    adapter.synthesize("물 좀 주세요");

    wireMock.verify(
        postRequestedFor(urlPathEqualTo("/tts-premium/v1/tts"))
            .withHeader("X-NCP-APIGW-API-KEY-ID", equalTo("key-id"))
            .withHeader("X-NCP-APIGW-API-KEY", equalTo("key"))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withRequestBody(containing("speaker=nara"))
            .withRequestBody(containing("format=mp3"))
            .withRequestBody(containing("text=%EB%AC%BC")));
  }

  @Test
  void 응답이_비어있으면_예외를_던진다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/tts-premium/v1/tts")).willReturn(aResponse().withBody(new byte[0])));

    assertThatThrownBy(() -> adapter.synthesize("물 좀 주세요"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 에러_응답이면_런타임_예외를_던진다() {
    wireMock.stubFor(
        post(urlPathEqualTo("/tts-premium/v1/tts")).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> adapter.synthesize("물 좀 주세요")).isInstanceOf(RuntimeException.class);
  }
}
