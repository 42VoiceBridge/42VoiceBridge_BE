package com.voicebridge.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(@NotBlank String kakaoAccessToken) {}
