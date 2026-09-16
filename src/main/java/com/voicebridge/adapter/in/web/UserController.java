package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.MyProfileResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.GetMyProfileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final GetMyProfileUseCase getMyProfileUseCase;

    @GetMapping("/me")
    public ApiResponse<MyProfileResponse> me(@AuthenticationPrincipal UUID userId) {
        var result = getMyProfileUseCase.getMyProfile(userId);
        return ApiResponse.success(MyProfileResponse.from(result));
    }
}
