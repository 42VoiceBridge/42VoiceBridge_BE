package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.SignUpUseCase;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SignUpService implements SignUpUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;

    @Override
    public SignUpResult signUp(SignUpCommand command) {
        if (userRepositoryPort.existsByEmail(command.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String hashedPassword = passwordEncoderPort.encode(command.rawPassword());
        User user = User.createLocal(command.email(), hashedPassword, command.nickname());
        User saved = userRepositoryPort.save(user);

        return new SignUpResult(saved.getId(), saved.getEmail(), saved.getNickname());
    }
}
