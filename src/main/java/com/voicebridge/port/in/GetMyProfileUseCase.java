package com.voicebridge.port.in;

import java.util.UUID;

public interface GetMyProfileUseCase {

    MyProfileResult getMyProfile(UUID userId);

    record MyProfileResult(UUID userId, String email, String nickname) {
    }
}
