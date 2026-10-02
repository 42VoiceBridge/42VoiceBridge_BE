package com.voicebridge.adapter.out.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.voicebridge.port.out.KakaoUserInfoPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 JavaScript SDK v2는 브라우저에서 액세스 토큰을 직접 받는 방식을 지원하지 않는다. 프론트는 Kakao.Auth.authorize()로 받은 인가 코드만
 * 보내고, 백엔드가 그 코드를 카카오 액세스 토큰으로 교환한 뒤 사용자 정보를 조회한다(OAuth2 Authorization Code Flow).
 *
 * <p>카카오 액세스 토큰은 이 어댑터 안에서만 쓰고 버린다. 저장하거나 밖으로 돌려주지 않는다.
 */
@Component
public class KakaoUserInfoAdapter implements KakaoUserInfoPort {

  private final RestClient restClient;
  private final String tokenUri;
  private final String userInfoUri;
  private final String clientId;
  private final String redirectUri;
  private final String clientSecret;

  public KakaoUserInfoAdapter(
      RestClient.Builder restClientBuilder,
      @Value("${voicebridge.kakao.token-uri}") String tokenUri,
      @Value("${voicebridge.kakao.user-info-uri}") String userInfoUri,
      @Value("${voicebridge.kakao.client-id}") String clientId,
      @Value("${voicebridge.kakao.redirect-uri}") String redirectUri,
      @Value("${voicebridge.kakao.client-secret}") String clientSecret) {
    this.restClient = restClientBuilder.build();
    this.tokenUri = tokenUri;
    this.userInfoUri = userInfoUri;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.clientSecret = clientSecret;
  }

  @Override
  public KakaoUserInfo fetchUserInfo(String authorizationCode) {
    return fetchUserInfoWithToken(exchangeForAccessToken(authorizationCode));
  }

  private String exchangeForAccessToken(String authorizationCode) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", clientId);
    // 프론트가 authorize()에 쓴 주소와 정확히 같아야 한다. 다르면 카카오가 KOE303으로 거절한다.
    form.add("redirect_uri", redirectUri);
    form.add("code", authorizationCode);
    // 카카오 콘솔에서 Client Secret을 켠 경우에만 필요하다. 켰는데 빠지면 KOE010으로 거절된다.
    if (clientSecret != null && !clientSecret.isBlank()) {
      form.add("client_secret", clientSecret);
    }

    KakaoTokenResponse response =
        restClient
            .post()
            .uri(tokenUri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(KakaoTokenResponse.class);

    if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
      throw new IllegalStateException("카카오 토큰 응답에 access_token이 없습니다.");
    }
    return response.accessToken();
  }

  @SuppressWarnings("unchecked")
  private KakaoUserInfo fetchUserInfoWithToken(String kakaoAccessToken) {
    Map<String, Object> response =
        restClient
            .get()
            .uri(userInfoUri)
            .header("Authorization", "Bearer " + kakaoAccessToken)
            .retrieve()
            .body(Map.class);

    String providerId = String.valueOf(response.get("id"));

    Map<String, Object> kakaoAccount =
        (Map<String, Object>) response.getOrDefault("kakao_account", Map.of());
    Map<String, Object> profile =
        (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());

    String email = (String) kakaoAccount.get("email");
    String nickname = (String) profile.getOrDefault("nickname", "카카오사용자");

    return new KakaoUserInfo(providerId, email, nickname);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {}
}
