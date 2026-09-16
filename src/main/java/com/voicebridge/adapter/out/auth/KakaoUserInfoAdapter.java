package com.voicebridge.adapter.out.auth;

import com.voicebridge.port.out.KakaoUserInfoPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class KakaoUserInfoAdapter implements KakaoUserInfoPort {

    private final RestClient restClient;
    private final String userInfoUri;

    public KakaoUserInfoAdapter(RestClient.Builder restClientBuilder,
                                 @Value("${voicebridge.kakao.user-info-uri}") String userInfoUri) {
        this.restClient = restClientBuilder.build();
        this.userInfoUri = userInfoUri;
    }

    @Override
    @SuppressWarnings("unchecked")
    public KakaoUserInfo fetchUserInfo(String kakaoAccessToken) {
        Map<String, Object> response = restClient.get()
                .uri(userInfoUri)
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(Map.class);

        String providerId = String.valueOf(response.get("id"));

        Map<String, Object> kakaoAccount = (Map<String, Object>) response.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());

        String email = (String) kakaoAccount.get("email");
        String nickname = (String) profile.getOrDefault("nickname", "카카오사용자");

        return new KakaoUserInfo(providerId, email, nickname);
    }
}
