package com.voicebridge.adapter.out.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import com.voicebridge.port.out.JamoStatsPort.JamoStatsResult;
import com.voicebridge.port.out.JamoStatsPort.TextPair;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

class HttpJamoStatsClientTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private static final String PATH = "/v1/analysis/jamo-errors";
  private static final List<TextPair> PAIRS = List.of(new TextPair("오늘 날씨가 좋습니다", "오늘 날씨가 조습니다"));

  private HttpJamoStatsClient client;

  @BeforeEach
  void setUp() {
    client = new HttpJamoStatsClient(RestClient.builder(), wireMock.baseUrl(), 3000, 10000);
  }

  @AfterEach
  void tearDown() {
    wireMock.resetAll();
  }

  private void aiResponds(String json) {
    wireMock.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(json)));
  }

  @Test
  void 우리_용어를_AI_계약의_필드명으로_바꿔서_보낸다() {
    aiResponds(
        """
        {"metric_version": "jamo-err-v1", "min_support": 20, "pairs_used": 1, "tokens": []}
        """);

    client.analyze(PAIRS, 20);

    wireMock.verify(
        postRequestedFor(urlPathEqualTo(PATH))
            // WireMock은 chunked 본문도 읽어서 이게 없으면 통과해 버린다. 실제 AI 서버는 Content-Length가 없으면 본문을 비어 있다고
            // 본다.
            .withHeader("Content-Length", matching("\\d+"))
            .withRequestBody(
                equalToJson(
                    """
                    {"pairs": [{"ref": "오늘 날씨가 좋습니다", "hyp": "오늘 날씨가 조습니다"}],
                     "min_support": 20}
                    """)));
  }

  @Test
  void AI_표기를_우리_표기로_바꾸고_표본이_부족하면_오류율을_null로_둔다() {
    aiResponds(
        """
        {"metric_version": "jamo-err-v1", "min_support": 20, "pairs_used": 1,
         "reference_tokens": 25,
         "tokens": [
           {"token": "ㅈ", "position": "initial", "errors": 12, "sample_count": 20,
            "error_rate": 0.6, "status": "ok", "silent_initial": false},
           {"token": "ㅆ", "position": "final", "errors": 3, "sample_count": 7,
            "error_rate": null, "status": "insufficient_data", "silent_initial": false}
         ]}
        """);

    JamoStatsResult result = client.analyze(PAIRS, 20);

    assertThat(result.metricVersion()).isEqualTo("jamo-err-v1");
    assertThat(result.minSupport()).isEqualTo(20);
    assertThat(result.pairsUsed()).isEqualTo(1);
    assertThat(result.tokens())
        .containsExactly(
            new JamoErrorStat("ㅈ", "INITIAL", 12, 20, 0.6, "OK"),
            new JamoErrorStat("ㅆ", "FINAL", 3, 7, null, "INSUFFICIENT_DATA"));
  }

  @Test
  void AI가_에러로_응답하면_AI_사용_불가_예외로_바꾼다() {
    wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client.analyze(PAIRS, 20))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_INFERENCE_UNAVAILABLE);
  }

  static Stream<Arguments> 계약을_어긴_응답() {
    String head = "\"metric_version\": \"jamo-err-v1\", \"min_support\": 20, \"pairs_used\": 1";
    String token =
        "{\"token\": \"ㅈ\", \"position\": \"initial\", \"errors\": 1, \"sample_count\": 20,"
            + " \"error_rate\": 0.05, \"status\": \"ok\"}";
    return Stream.of(
        Arguments.of(
            "계산 버전(metric_version)이 없음",
            "{\"min_support\": 20, \"pairs_used\": 1, \"tokens\": []}"),
        Arguments.of(
            "min_support가 없음",
            "{\"metric_version\": \"jamo-err-v1\", \"pairs_used\": 1, \"tokens\": []}"),
        Arguments.of(
            "요청과 다른 min_support",
            "{\"metric_version\": \"jamo-err-v1\", \"min_support\": 5, \"pairs_used\": 1,"
                + " \"tokens\": []}"),
        Arguments.of(
            "pairs_used가 없음",
            "{\"metric_version\": \"jamo-err-v1\", \"min_support\": 20, \"tokens\": []}"),
        Arguments.of("자모 항목이 null", "{" + head + ", \"tokens\": [null]}"),
        Arguments.of(
            "sample_count가 없음",
            "{" + head + ", \"tokens\": [" + token.replace(" \"sample_count\": 20,", "") + "]}"),
        Arguments.of(
            "계약에 없는 position",
            "{" + head + ", \"tokens\": [" + token.replace("initial", "onset") + "]}"),
        Arguments.of(
            "OK인데 오류율이 null",
            "{" + head + ", \"tokens\": [" + token.replace("0.05", "null") + "]}"),
        Arguments.of(
            "표본 부족인데 오류율이 있음",
            "{"
                + head
                + ", \"tokens\": ["
                + token.replace("\"ok\"", "\"insufficient_data\"")
                + "]}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("계약을_어긴_응답")
  void 계약을_어긴_응답은_사용자_오류가_아니라_AI_사용_불가로_바꾼다(String 경우, String json) {
    aiResponds(json);

    // 그대로 넘기면 JamoErrorSnapshot이 IllegalArgumentException을 던져 400(사용자 요청 오류)이 되거나, 빈 값이 0으로 저장된다
    assertThatThrownBy(() -> client.analyze(PAIRS, 20))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_INFERENCE_UNAVAILABLE);
  }
}
