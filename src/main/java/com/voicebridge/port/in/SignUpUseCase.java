package com.voicebridge.port.in;

import java.util.UUID;

public interface SignUpUseCase {

    SignUpResult signUp(SignUpCommand command);

    record SignUpCommand(String email, String rawPassword, String nickname) {
    }

    record SignUpResult(UUID userId, String email, String nickname) {
    }
}
