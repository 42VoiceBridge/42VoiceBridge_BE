package com.voicebridge.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequestTtsRequest(@NotNull UUID confirmationId, @NotNull UUID idempotencyKey) {}
