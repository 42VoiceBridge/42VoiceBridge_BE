package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.GetMyProfileUseCase;
import com.voicebridge.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyProfileService implements GetMyProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public MyProfileResult getMyProfile(UUID userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        return new MyProfileResult(user.getId(), user.getEmail(), user.getNickname());
    }
}
