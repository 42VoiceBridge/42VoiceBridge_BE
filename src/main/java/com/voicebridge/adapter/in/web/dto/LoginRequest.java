package com.voicebridge.adapter.in.web.dto;

import com.voicebridge.port.in.LoginUseCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
  public LoginUseCase.LoginCommand toCommand() {
    return new LoginUseCase.LoginCommand(email, password);
  }
}
