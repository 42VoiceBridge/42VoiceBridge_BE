package com.voicebridge.adapter.out.stt;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.SpeechToTextPort;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 네이버 클라우드 CLOVA Speech Recognition(CSR) 어댑터.
 *
 * <p>계약: {@code POST {base-url}?lang=Kor}, 헤더 {@code X-NCP-APIGW-API-KEY-ID}/{@code
 * X-NCP-APIGW-API-KEY}, 본문은 {@code application/octet-stream} 오디오 바이너리(60초 이하), 응답은 {@code {"text":
 * "..."}}. 2026-10-30 공식 문서(api.ncloud-docs.com, ai-naver-clovaspeechrecognition-stt)의 검색 결과로 확인했다
 * — 이 컨테이너에서 문서 원문은 열리지 않았고, 실제 키 왕복도 아직 확인 전이다.
 *
 * <p>생성자는 설정값만 받고 네트워크를 쓰지 않는다. 키가 없으면 호출하지 않고 503으로 끝낸다(키 없는 CI에서도 컨텍스트가 뜬다). 들은 말과 음성은 로그에 남기지
 * 않는다.
 */
@Slf4j
@Component
// local 프로파일은 StubSpeechToTextAdapter를 쓴다(키 없이 대화 모드 확인용).
@Profile("!local")
public class NaverClovaSpeechAdapter implements SpeechToTextPort {

  private static final String API_KEY_ID_HEADER = "X-NCP-APIGW-API-KEY-ID";
  private static final String API_KEY_HEADER = "X-NCP-APIGW-API-KEY";
  private static final String LANGUAGE = "Kor";

  private final RestClient restClient;
  private final String sttUri;
  private final String apiKeyId;
  private final String apiKey;

  public NaverClovaSpeechAdapter(
      RestClient.Builder restClientBuilder,
      @Value("${voicebridge.stt.naver.base-url}") String sttUri,
      @Value("${voicebridge.stt.naver.api-key-id:}") String apiKeyId,
      @Value("${voicebridge.stt.naver.api-key:}") String apiKey,
      @Value("${voicebridge.stt.naver.timeout}") Duration timeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(timeout);
    requestFactory.setReadTimeout(timeout);
    this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    this.sttUri = sttUri;
    this.apiKeyId = apiKeyId;
    this.apiKey = apiKey;
  }

  @Override
  public String transcribe(byte[] wavBytes) {
    if (wavBytes == null || wavBytes.length == 0) {
      throw new IllegalArgumentException("인식할 음성이 없습니다.");
    }
    if (apiKeyId == null || apiKeyId.isBlank() || apiKey == null || apiKey.isBlank()) {
      log.warn("[STT] CSR 키가 설정되지 않음");
      throw new CustomException(ErrorCode.STT_UNAVAILABLE);
    }

    SttResponse response;
    try {
      response =
          restClient
              .post()
              .uri(sttUri + "?lang={lang}", LANGUAGE)
              .header(API_KEY_ID_HEADER, apiKeyId)
              .header(API_KEY_HEADER, apiKey)
              .contentType(MediaType.APPLICATION_OCTET_STREAM)
              .body(wavBytes)
              .retrieve()
              .body(SttResponse.class);
    } catch (RestClientResponseException e) {
      // 응답 본문에는 오류 코드만 오지만, 혹시 몰라 상태 코드만 남긴다
      log.warn("[STT] CSR 응답 오류: status={}", e.getStatusCode().value());
      throw new CustomException(ErrorCode.STT_UNAVAILABLE);
    } catch (RestClientException e) {
      // 연결 실패·시간 초과·응답 형식 오류
      log.warn("[STT] CSR 호출 실패: {}", e.getClass().getSimpleName());
      throw new CustomException(ErrorCode.STT_UNAVAILABLE);
    }

    if (response == null || response.text() == null || response.text().isBlank()) {
      log.warn("[STT] 인식 결과가 비어 있음");
      throw new CustomException(ErrorCode.STT_UNAVAILABLE);
    }
    return response.text().strip();
  }

  private record SttResponse(String text) {}
}
