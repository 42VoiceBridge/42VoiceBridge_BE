package com.voicebridge.port.in;

import java.util.UUID;

public interface GetMyProfileUseCase {

    MyProfileResult getMyProfile(UUID userId);

    // hasPersonalizedModel 등 개인화 관련 필드는 personalization 도메인 구현 후 추가한다(YAGNI).
    record MyProfileResult(UUID userId, String email, String nickname) {
    }
}
