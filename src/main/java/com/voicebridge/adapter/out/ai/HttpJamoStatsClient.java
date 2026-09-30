package com.voicebridge.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import com.voicebridge.port.out.JamoStatsPort;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 인식 어댑터와 달리 jpyrust-experiment 프로파일에서도 켜진다. 자모 통계는 JPyRust 쪽 대체 구현이 없어서, 여기서 빠지면 그 프로파일에서
// 이 포트를 쓰는 빈이 뜨지 못한다.
@Slf4j
@Component
@Profile("!local")
public class HttpJamoStatsClient implements JamoStatsPort {

  private static final String PATH = "/v1/analysis/jamo-errors";

  // AI 표기(소문자)를 우리 API 표기(대문자)로 바꾼다. toUpperCase()로 일괄 변환하지 않는 이유는, AI가 계약에 없는 값을 보내기
  // 시작했을 때 그 값이 그대로 프론트까지 흘러가지 않고 여기서 멈추게 하기 위해서다.
  private static final Map<String, String> POSITIONS =
      Map.of("initial", "INITIAL", "medial", "MEDIAL", "final", "FINAL");
  private static final String STATUS_OK = "ok";
  private static final Map<String, String> STATUSES =
      Map.of(STATUS_OK, "OK", "insufficient_data", "INSUFFICIENT_DATA");

  private final RestClient restClient;
  private final String baseUrl;

  public HttpJamoStatsClient(
      RestClient.Builder restClientBuilder,
      @Value("${voicebridge.ai.http.base-url}") String baseUrl,
      @Value("${voicebridge.ai.http.connect-timeout-ms}") int connectTimeoutMs,
      @Value("${voicebridge.ai.http.read-timeout-ms}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    // JSON 본문을 한 번에 모아 Content-Length를 붙여 보낸다. 감싸지 않으면 chunked로 흘려보내는데, AI 데모 서버는
    // Content-Length로만 본문을 읽어서 빈 요청(no_pairs, 422)으로 처리한다. 요청이 텍스트 쌍뿐이라 버퍼링 부담은 없다.
    this.restClient =
        restClientBuilder
            .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
            .build();
    this.baseUrl = baseUrl;
  }

  @Override
  public JamoStatsResult analyze(List<TextPair> pairs, int minSupport) {
    JamoErrorsResponse response;
    try {
      response =
          restClient
              .post()
              .uri(baseUrl + PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .body(JamoErrorsRequest.from(pairs, minSupport))
              .retrieve()
              .body(JamoErrorsResponse.class);
    } catch (RestClientException e) {
      // CustomException은 원인 예외를 담지 못해서, 버리기 전에 로그로 남긴다
      log.warn("[자모 통계] AI 호출 실패 pairs={}", pairs.size(), e);
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE, "자모 오류 통계를 계산하지 못했습니다.");
    }

    String violation = findContractViolation(response, minSupport);
    if (violation != null) {
      // IllegalStateException을 쓰지 않는 이유: 전역 핸들러가 그것을 409(상태 전이 위반)로 바꾸므로, 요청 흐름에서 터지면
      // 프론트는 AI 문제를 자기 요청의 상태 문제로 오해하게 된다. AI를 제대로 쓸 수 없다는 뜻이니 통신 실패와 같은 503으로 보낸다.
      log.error("[자모 통계] AI 응답이 계약(jamo-err-v1)과 다릅니다: {}", violation);
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE, "자모 오류 통계를 계산하지 못했습니다.");
    }
    return new JamoStatsResult(
        response.metricVersion(),
        response.minSupport(),
        response.pairsUsed(),
        response.tokens().stream().map(HttpJamoStatsClient::toJamoErrorStat).toList());
  }

  /**
   * 계약을 어긴 곳을 설명으로 돌려준다. 문제가 없으면 null. 여기서 걸러야 하는 이유: 빈 값이 도메인까지 가면 도메인 검증이
   * IllegalArgumentException을 던지고, 전역 핸들러가 그것을 사용자 요청 오류(400)로 바꾼다.
   */
  private static String findContractViolation(
      JamoErrorsResponse response, int requestedMinSupport) {
    if (response == null || response.tokens() == null) {
      return "tokens 없음";
    }
    if (isBlank(response.metricVersion())) {
      return "metric_version 없음";
    }
    // 스냅샷에 그대로 저장되는 값이다. 빠져서 0이 되면 설정값과 늘 달라 보여서, 조회할 때마다 AI를 다시 부른다.
    if (response.minSupport() == null || response.minSupport() != requestedMinSupport) {
      return "min_support 불일치 요청=" + requestedMinSupport + " 응답=" + response.minSupport();
    }
    if (response.pairsUsed() == null) {
      return "pairs_used 없음";
    }
    for (Token token : response.tokens()) {
      String violation = findContractViolation(token);
      if (violation != null) {
        return violation + " " + token;
      }
    }
    return null;
  }

  private static String findContractViolation(Token token) {
    if (token == null || isBlank(token.token())) {
      return "자모가 빈 항목";
    }
    // 원시 타입으로 받으면 빠진 값이 조용히 0이 되어 "표본 0개"로 보인다
    if (token.errors() == null || token.sampleCount() == null) {
      return "errors나 sample_count가 빈 항목";
    }
    // Map.of로 만든 표는 null을 물으면 false가 아니라 NullPointerException을 던진다. 빠진 값을 먼저 걸러야 503으로 간다.
    if (token.position() == null || token.status() == null) {
      return "position이나 status가 빈 항목";
    }
    // 위 표로 바꿀 수 없는 값(계약에 없는 값)
    if (!POSITIONS.containsKey(token.position()) || !STATUSES.containsKey(token.status())) {
      return "알 수 없는 position이나 status";
    }
    // 프론트는 OK면 오류율을 보여주고 표본 부족이면 null을 기대한다. 둘이 어긋나면 화면이 깨진다.
    if (STATUS_OK.equals(token.status()) != (token.errorRate() != null)) {
      return "status와 error_rate가 맞지 않는 항목";
    }
    return null;
  }

  private static JamoErrorStat toJamoErrorStat(Token token) {
    return new JamoErrorStat(
        token.token(),
        POSITIONS.get(token.position()),
        token.errors(),
        token.sampleCount(),
        token.errorRate(),
        STATUSES.get(token.status()));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record JamoErrorsRequest(List<Pair> pairs, @JsonProperty("min_support") int minSupport) {

    static JamoErrorsRequest from(List<TextPair> pairs, int minSupport) {
      return new JamoErrorsRequest(
          pairs.stream().map(p -> new Pair(p.answerText(), p.recognizedText())).toList(),
          minSupport);
    }
  }

  private record Pair(String ref, String hyp) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record JamoErrorsResponse(
      @JsonProperty("metric_version") String metricVersion,
      @JsonProperty("min_support") Integer minSupport,
      @JsonProperty("pairs_used") Integer pairsUsed,
      List<Token> tokens) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Token(
      String token,
      String position,
      Integer errors,
      @JsonProperty("sample_count") Integer sampleCount,
      @JsonProperty("error_rate") Double errorRate,
      String status) {}
}
