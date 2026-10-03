package com.voicebridge.adapter.out.tts;

import com.voicebridge.port.out.TtsEnginePort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 클라우드 플랫폼(NCP) CLOVA Voice TTS Premium 어댑터.
 *
 * <p>API 계약(엔드포인트, 헤더, 폼 필드, 응답이 오디오 바이너리 직접 반환)은
 * https://api.ncloud-docs.com/docs/ai-naver-clovavoice-ttspremium 문서를 기준으로 확인했다 — 요청은 {@code
 * application/x-www-form-urlencoded}, 응답은 JSON/base64로 감싸지 않은 mp3/wav 바이너리 그대로다.
 */
@Component
public class NaverClovaVoiceAdapter implements TtsEnginePort {

  private static final String API_KEY_ID_HEADER = "X-NCP-APIGW-API-KEY-ID";
  private static final String API_KEY_HEADER = "X-NCP-APIGW-API-KEY";

  private final RestClient restClient;
  private final String ttsUri;
  private final String apiKeyId;
  private final String apiKey;
  private final String speaker;

  public NaverClovaVoiceAdapter(
      RestClient.Builder restClientBuilder,
      @Value("${voicebridge.tts.naver.base-url}") String ttsUri,
      @Value("${voicebridge.tts.naver.api-key-id}") String apiKeyId,
      @Value("${voicebridge.tts.naver.api-key}") String apiKey,
      @Value("${voicebridge.tts.naver.speaker}") String speaker) {
    this.restClient = restClientBuilder.build();
    this.ttsUri = ttsUri;
    this.apiKeyId = apiKeyId;
    this.apiKey = apiKey;
    this.speaker = speaker;
  }

  @Override
  public byte[] synthesize(String text) {
    byte[] audio =
        restClient
            .post()
            .uri(ttsUri)
            .header(API_KEY_ID_HEADER, apiKeyId)
            .header(API_KEY_HEADER, apiKey)
            .contentType(MediaType.valueOf("application/x-www-form-urlencoded;charset=UTF-8"))
            .body(formBody(text))
            .retrieve()
            .body(byte[].class);

    if (audio == null || audio.length == 0) {
      throw new IllegalStateException("CLOVA Voice 응답이 비어 있습니다.");
    }
    return audio;
  }

  // 계약상 text는 UTF-8로 인코딩돼야 한다 — Spring 기본 폼 컨버터 설정에 기대지 않고 직접 인코딩해 명시적으로 보장한다.
  private String formBody(String text) {
    return "speaker=" + urlEncode(speaker) + "&text=" + urlEncode(text) + "&format=mp3";
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
