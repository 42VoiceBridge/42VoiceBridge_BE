package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.GetMyProfileUseCase;

import java.util.UUID;

public record MyProfileResponse(UUID userId, String email, String nickname) {
    public static MyProfileResponse from(GetMyProfileUseCase.MyProfileResult result) {
        return new MyProfileResponse(result.userId(), result.email(), result.nickname());
    }
}
