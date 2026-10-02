package com.voicebridge.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

// 프론트가 Kakao.Auth.authorize()로 받은 인가 코드. 카카오 액세스 토큰은 백엔드가 이 코드로 교환해 얻는다.
public record KakaoLoginRequest(@NotBlank String authorizationCode) {}
