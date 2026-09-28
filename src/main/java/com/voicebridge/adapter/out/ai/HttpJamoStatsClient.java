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
  private static final Map<String, String> STATUSES =
      Map.of("ok", "OK", "insufficient_data", "INSUFFICIENT_DATA");

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

    if (response == null || response.tokens() == null) {
      throw contractViolation("tokens가 없습니다");
    }
    return new JamoStatsResult(
        response.metricVersion(),
        response.minSupport(),
        response.pairsUsed(),
        response.tokens().stream().map(this::toJamoErrorStat).toList());
  }

  private JamoErrorStat toJamoErrorStat(Token token) {
    String position = POSITIONS.get(token.position());
    String status = STATUSES.get(token.status());
    if (position == null || status == null) {
      throw contractViolation(
          "알 수 없는 값 position=" + token.position() + " status=" + token.status());
    }
    return new JamoErrorStat(
        token.token(), position, token.errors(), token.sampleCount(), token.errorRate(), status);
  }

  // IllegalStateException을 쓰지 않는 이유: 전역 핸들러가 그것을 409(상태 전이 위반)로 바꾸므로, 요청 흐름에서 터지면
  // 프론트는 AI 문제를 자기 요청의 상태 문제로 오해하게 된다. AI를 제대로 쓸 수 없다는 뜻이니 통신 실패와 같은 503으로 보낸다.
  private CustomException contractViolation(String detail) {
    log.error("[자모 통계] AI 응답이 계약(jamo-err-v1)과 다릅니다: {}", detail);
    return new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE, "자모 오류 통계를 계산하지 못했습니다.");
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
      @JsonProperty("min_support") int minSupport,
      @JsonProperty("pairs_used") int pairsUsed,
      List<Token> tokens) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Token(
      String token,
      String position,
      int errors,
      @JsonProperty("sample_count") int sampleCount,
      @JsonProperty("error_rate") Double errorRate,
      String status) {}
}
