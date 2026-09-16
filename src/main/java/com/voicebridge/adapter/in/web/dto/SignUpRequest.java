package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.SignUpUseCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String nickname
) {
    public SignUpUseCase.SignUpCommand toCommand() {
        return new SignUpUseCase.SignUpCommand(email, password, nickname);
    }
}
