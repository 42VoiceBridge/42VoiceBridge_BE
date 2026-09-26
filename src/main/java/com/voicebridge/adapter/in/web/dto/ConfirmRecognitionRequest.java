package com.voicebridge.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRecognitionRequest(@NotBlank String confirmedText) {}
