package com.voicebridge.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.EnrollmentPromptPort;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 추천 문장에는 JPyRust 대체 구현이 없어 jpyrust-experiment 프로파일에서도 켜진다(자모 통계 어댑터와 같은 이유).
@Slf4j
@Component
@Profile("!local")
public class HttpEnrollmentPromptClient implements EnrollmentPromptPort {

  private static final String PATH = "/v1/enroll/next-prompts";

  // AI v1은 random만 구현했다. coverage·error_based는 501이 돌아온다(AI 계약 §3.6).
  private static final String STRATEGY = "random";

  private final RestClient restClient;
  private final String baseUrl;

  public HttpEnrollmentPromptClient(
      RestClient.Builder restClientBuilder,
      @Value("${voicebridge.ai.http.base-url}") String baseUrl,
      @Value("${voicebridge.ai.http.connect-timeout-ms}") int connectTimeoutMs,
      @Value("${voicebridge.ai.http.read-timeout-ms}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    // JSON 본문을 한 번에 모아 Content-Length를 붙여 보낸다. 감싸지 않으면 chunked로 흘려보내는데, AI 데모 서버는
    // Content-Length로만 본문을 읽어서 빈 요청으로 처리한다.
    this.restClient =
        restClientBuilder
            .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
            .build();
    this.baseUrl = baseUrl;
  }

  @Override
  public PromptBatch nextPrompts(
      UUID userId, int count, long seed, Collection<String> excludePromptIds) {
    NextPromptsResponse response;
    try {
      response =
          restClient
              .post()
              .uri(baseUrl + PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  new NextPromptsRequest(
                      userId.toString(), count, STRATEGY, seed, List.copyOf(excludePromptIds)))
              .retrieve()
              .body(NextPromptsResponse.class);
    } catch (RestClientException e) {
      // CustomException은 원인 예외를 담지 못해서, 버리기 전에 로그로 남긴다
      log.warn("[추천 문장] AI 호출 실패 count={} exclude={}", count, excludePromptIds.size(), e);
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE, "추천 문장을 받지 못했습니다.");
    }

    // 계약을 어긴 응답을 그대로 넘기면 도메인(ShownPrompt)이 IllegalArgumentException으로 거절하고, 전역 핸들러가 그걸
    // 400(사용자 요청 오류)으로 바꾼다. 사용자는 잘못한 게 없으니 여기서 AI 연동 오류(503)로 멈춘다.
    String violation = findContractViolation(response, seed);
    if (violation != null) {
      log.error("[추천 문장] AI 응답이 계약과 다릅니다: {}", violation);
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE, "추천 문장을 받지 못했습니다.");
    }
    return new PromptBatch(
        response.strategy(),
        response.strategyVersion(),
        response.seed(),
        response.prompts().stream().map(p -> new Prompt(p.promptId(), p.text())).toList());
  }

  /** 계약을 어긴 곳을 설명으로 돌려준다. 문제가 없으면 null. */
  private static String findContractViolation(NextPromptsResponse response, long requestedSeed) {
    if (response == null || response.prompts() == null) {
      return "prompts 없음";
    }
    // 전략·버전·seed는 제안 기록에 그대로 남는다. 틀린 값이면 나중에 같은 추천을 재현할 수 없다.
    if (!STRATEGY.equals(response.strategy())) {
      return "요청하지 않은 전략 " + response.strategy();
    }
    if (isBlank(response.strategyVersion())) {
      return "strategy_version 없음";
    }
    // 원시 타입으로 받으면 seed가 빠졌을 때 조용히 0이 기록된다
    if (response.seed() == null || response.seed() != requestedSeed) {
      return "seed 불일치 요청=" + requestedSeed + " 응답=" + response.seed();
    }
    for (PromptItem prompt : response.prompts()) {
      if (prompt == null || isBlank(prompt.promptId()) || isBlank(prompt.text())) {
        return "prompt_id나 text가 빈 문장 항목 " + prompt;
      }
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record NextPromptsRequest(
      @JsonProperty("user_id") String userId,
      int n,
      String strategy,
      long seed,
      @JsonProperty("exclude_prompt_ids") List<String> excludePromptIds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record NextPromptsResponse(
      String strategy,
      @JsonProperty("strategy_version") String strategyVersion,
      Long seed,
      List<PromptItem> prompts) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PromptItem(@JsonProperty("prompt_id") String promptId, String text) {}
}
