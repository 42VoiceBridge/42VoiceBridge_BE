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
import com.voicebridge.port.out.EnrollmentPromptPort.Prompt;
import com.voicebridge.port.out.EnrollmentPromptPort.PromptBatch;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

class HttpEnrollmentPromptClientTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private static final String PATH = "/v1/enroll/next-prompts";

  private HttpEnrollmentPromptClient client;
  private final UUID userId = UUID.fromString("11111111-2222-3333-4444-555555555555");

  @BeforeEach
  void setUp() {
    client = new HttpEnrollmentPromptClient(RestClient.builder(), wireMock.baseUrl(), 3000, 10000);
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
  void AI_계약의_필드명으로_random_전략을_요청한다() {
    aiResponds(
        """
        {"strategy": "random", "strategy_version": "prompt-random-v1", "seed": 42,
         "pool_size": 1807, "prompts": []}
        """);

    client.nextPrompts(userId, 10, 42L, List.of("02-03-0001"));

    wireMock.verify(
        postRequestedFor(urlPathEqualTo(PATH))
            // WireMock은 chunked 본문도 읽어서 이게 없으면 통과해 버린다. 실제 AI 서버는 Content-Length가 없으면 본문을 비어 있다고
            // 본다.
            .withHeader("Content-Length", matching("\\d+"))
            .withRequestBody(
                equalToJson(
                    """
                    {"user_id": "11111111-2222-3333-4444-555555555555", "n": 10,
                     "strategy": "random", "seed": 42, "exclude_prompt_ids": ["02-03-0001"]}
                    """)));
  }

  @Test
  void AI_표기를_우리_표기로_바꾼다() {
    aiResponds(
        """
        {"strategy": "random", "strategy_version": "prompt-random-v1", "seed": 42,
         "pool_size": 1807,
         "prompts": [{"prompt_id": "02-03-0001", "text": "식당이 어디예요?"},
                     {"prompt_id": "06-01-0100", "text": "서울역으로 가주세요."}]}
        """);

    PromptBatch batch = client.nextPrompts(userId, 2, 42L, List.of());

    assertThat(batch.strategy()).isEqualTo("random");
    assertThat(batch.strategyVersion()).isEqualTo("prompt-random-v1");
    assertThat(batch.seed()).isEqualTo(42L);
    assertThat(batch.prompts())
        .containsExactly(
            new Prompt("02-03-0001", "식당이 어디예요?"), new Prompt("06-01-0100", "서울역으로 가주세요."));
  }

  @Test
  void AI가_에러로_응답하면_AI_사용_불가_예외로_바꾼다() {
    // 문장 풀 파일이 없으면 AI 데모 서버는 503(prompt_pool_missing)을 돌려준다
    wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> client.nextPrompts(userId, 10, 42L, List.of()))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_INFERENCE_UNAVAILABLE);
  }

  @Test
  void 전략_버전이_없으면_기록할_수_없으므로_멈춘다() {
    aiResponds(
        """
        {"strategy": "random", "seed": 42, "prompts": [{"prompt_id": "02-03-0001", "text": "가"}]}
        """);

    assertThatThrownBy(() -> client.nextPrompts(userId, 10, 42L, List.of()))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_INFERENCE_UNAVAILABLE);
  }

  // 요청 seed는 42. 각 응답은 한 군데만 계약을 어긴다
  static Stream<Arguments> 계약을_어긴_응답() {
    String ok = "\"strategy\": \"random\", \"strategy_version\": \"prompt-random-v1\"";
    return Stream.of(
        Arguments.of(
            "문장 원문(text)이 없음",
            "{" + ok + ", \"seed\": 42, \"prompts\": [{\"prompt_id\": \"02-03-0001\"}]}"),
        Arguments.of(
            "문장 ID가 공백",
            "{" + ok + ", \"seed\": 42, \"prompts\": [{\"prompt_id\": \" \", \"text\": \"가\"}]}"),
        Arguments.of("문장 항목이 null", "{" + ok + ", \"seed\": 42, \"prompts\": [null]}"),
        Arguments.of("seed가 없음", "{" + ok + ", \"prompts\": []}"),
        Arguments.of("요청과 다른 seed", "{" + ok + ", \"seed\": 7, \"prompts\": []}"),
        Arguments.of(
            "요청하지 않은 전략",
            "{\"strategy\": \"error_based\", \"strategy_version\": \"v1\", \"seed\": 42,"
                + " \"prompts\": []}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("계약을_어긴_응답")
  void 계약을_어긴_응답은_사용자_오류가_아니라_AI_사용_불가로_바꾼다(String 경우, String json) {
    aiResponds(json);

    // 그대로 넘기면 ShownPrompt가 IllegalArgumentException을 던져 400(사용자 요청 오류)이 된다
    assertThatThrownBy(() -> client.nextPrompts(userId, 10, 42L, List.of()))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_INFERENCE_UNAVAILABLE);
  }
}
