package com.voicebridge.adapter.out.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.voicebridge.port.out.KakaoUserInfoPort.KakaoUserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class KakaoUserInfoAdapterTest {

  @RegisterExtension
  static WireMockExtension kakao =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private static final String TOKEN_PATH = "/oauth/token";
  private static final String USER_ME_PATH = "/v2/user/me";
  private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao/callback";

  @AfterEach
  void tearDown() {
    kakao.resetAll();
  }

  private KakaoUserInfoAdapter adapter(String clientSecret) {
    return new KakaoUserInfoAdapter(
        RestClient.builder(),
        kakao.baseUrl() + TOKEN_PATH,
        kakao.baseUrl() + USER_ME_PATH,
        "rest-api-key",
        REDIRECT_URI,
        clientSecret);
  }

  private void kakaoIssuesToken() {
    kakao.stubFor(
        post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(okJson("{\"token_type\": \"bearer\", \"access_token\": \"kakao-at\"}")));
  }

  private void kakaoReturnsUser() {
    kakao.stubFor(
        get(urlPathEqualTo(USER_ME_PATH))
            .willReturn(
                okJson(
                    """
                    {"id": 12345,
                     "kakao_account": {"email": "user@kakao.com", "profile": {"nickname": "민수"}}}
                    """)));
  }

  @Test
  void 인가_코드를_카카오_토큰으로_바꾼_뒤_그_토큰으로_사용자_정보를_조회한다() {
    kakaoIssuesToken();
    kakaoReturnsUser();

    KakaoUserInfo info = adapter("").fetchUserInfo("auth-code");

    assertThat(info).isEqualTo(new KakaoUserInfo("12345", "user@kakao.com", "민수"));
    kakao.verify(
        postRequestedFor(urlPathEqualTo(TOKEN_PATH))
            .withFormParam("grant_type", equalTo("authorization_code"))
            .withFormParam("client_id", equalTo("rest-api-key"))
            .withFormParam("redirect_uri", equalTo(REDIRECT_URI))
            .withFormParam("code", equalTo("auth-code"))
            // Client Secret을 켜지 않았으면 보내지 않는다
            .withRequestBody(notContaining("client_secret")));
    kakao.verify(
        getRequestedFor(urlPathEqualTo(USER_ME_PATH))
            .withHeader("Authorization", equalTo("Bearer kakao-at")));
  }

  @Test
  void Client_Secret이_설정돼_있으면_토큰_교환에_함께_보낸다() {
    kakaoIssuesToken();
    kakaoReturnsUser();

    adapter("kakao-secret").fetchUserInfo("auth-code");

    kakao.verify(
        postRequestedFor(urlPathEqualTo(TOKEN_PATH))
            .withFormParam("client_secret", equalTo("kakao-secret")));
  }

  @Test
  void 카카오가_인가_코드를_거절하면_사용자_정보를_조회하지_않고_실패한다() {
    // 재사용하거나 만료된 인가 코드(KOE320)
    kakao.stubFor(
        post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": \"invalid_grant\", \"error_code\": \"KOE320\"}")));

    assertThatThrownBy(() -> adapter("").fetchUserInfo("used-code"))
        .isInstanceOf(RestClientResponseException.class);
    kakao.verify(0, getRequestedFor(urlPathEqualTo(USER_ME_PATH)));
  }

  @Test
  void 토큰_응답에_access_token이_없으면_실패한다() {
    kakao.stubFor(
        post(urlPathEqualTo(TOKEN_PATH)).willReturn(okJson("{\"token_type\": \"bearer\"}")));

    assertThatThrownBy(() -> adapter("").fetchUserInfo("auth-code"))
        .isInstanceOf(IllegalStateException.class);
    kakao.verify(0, getRequestedFor(urlPathEqualTo(USER_ME_PATH)));
  }
}
